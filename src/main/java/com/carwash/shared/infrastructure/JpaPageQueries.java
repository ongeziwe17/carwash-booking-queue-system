package com.carwash.shared.infrastructure;

import com.carwash.shared.application.*;
import com.carwash.shared.exception.BusinessRuleViolationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.TransactionDefinition;
import java.time.*;
import java.util.*;

/** Query mechanics only. Owned adapters supply static JPQL mappings and mandatory scope predicates. */
@Component
@Profile("postgres")
public class JpaPageQueries {
    @PersistenceContext private EntityManager entityManager;
    public record Field(String expression, String nano, boolean text) {
        public static Field value(String expression) { return new Field(expression, null, false); }
        public static Field text(String expression) { return new Field(expression, null, true); }
        public static Field time(String expression, String nano) { return new Field(expression, nano, false); }
    }
    public <T> PageResult<T> find(Class<T> type, ListQuery request, String scope,
                                 Map<String, Object> scopeParameters, Map<String, Field> fields) {
        Integer isolation = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        if (!TransactionSynchronizationManager.isActualTransactionActive() || isolation == null
                || isolation < TransactionDefinition.ISOLATION_REPEATABLE_READ)
            throw new IllegalStateException("Paged queries require a repeatable-read snapshot");
        var parameters = new LinkedHashMap<>(scopeParameters);
        var predicates = new ArrayList<String>(); predicates.add(scope);
        int index = 0;
        for (var filter : request.filters().entrySet()) {
            Field field = required(fields, filter.getKey()); String parameter = "filter" + index++;
            Object value = filter.getValue();
            String operator = filter.getKey().equals("from") ? ">=" : filter.getKey().equals("to") ? "<=" : "=";
            if (field.nano() != null && (value instanceof LocalDateTime || value instanceof Instant)) {
                Object main; Object nano;
                if (value instanceof LocalDateTime time) { main = PersistenceSupport.databaseTime(time); nano = PersistenceSupport.nanoRemainder(time); }
                else { var instant = (Instant) value; main = instant.getEpochSecond(); nano = instant.getNano(); }
                if (operator.equals("=")) {
                    predicates.add("(" + field.expression() + " = :" + parameter + " and " + field.nano()
                            + " = :" + parameter + "Nano)");
                } else {
                    String strict = operator.equals(">=") ? ">" : "<";
                    predicates.add("(" + field.expression() + " " + strict + " :" + parameter
                            + " or (" + field.expression() + " = :" + parameter + " and " + field.nano()
                            + " " + operator + " :" + parameter + "Nano))");
                }
                parameters.put(parameter, main); parameters.put(parameter + "Nano", nano);
            } else {
                predicates.add(field.expression() + " " + operator + " :" + parameter);
                parameters.put(parameter, value);
            }
        }
        String from = " from " + type.getSimpleName() + " q where " + String.join(" and ", predicates);
        var count = entityManager.createQuery("select count(q)" + from, Long.class);
        parameters.forEach(count::setParameter); long total = count.getSingleResult();
        if (request.page().offset() >= total) return PageResult.of(List.of(), request.page(), total);
        var ordering = new ArrayList<String>();
        for (var term : request.sort()) {
            Field field = required(fields, term.field()); String direction = term.descending() ? " desc" : " asc";
            String expression = field.text() ? "collate(" + field.expression() + " as `C`)" : field.expression();
            ordering.add(expression + direction + " nulls last");
            if (field.nano() != null) ordering.add(field.nano() + direction);
        }
        var content = entityManager.createQuery("select q" + from + " order by " + String.join(", ", ordering), type)
                .setFirstResult(request.page().offset()).setMaxResults(request.page().size());
        parameters.forEach(content::setParameter);
        return PageResult.of(content.getResultList(), request.page(), total);
    }
    private static Field required(Map<String, Field> fields, String key) {
        Field field = fields.get(key);
        if (field == null) throw new BusinessRuleViolationException("Unsupported list field");
        return field;
    }
}
