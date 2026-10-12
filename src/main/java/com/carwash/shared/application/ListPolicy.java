package com.carwash.shared.application;

import com.carwash.shared.exception.BusinessRuleViolationException;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** Resource-specific allowlists are supplied by the owning capability. No persistence names escape. */
public record ListPolicy(Set<String> sortFields, List<SortOrder> defaultSort,
                         Map<String, Function<String, Object>> filterFields, Set<String> contextParameters) {
    public ListPolicy { sortFields = Set.copyOf(sortFields); defaultSort = List.copyOf(defaultSort);
        var filters = new java.util.LinkedHashMap<>(filterFields);
        if (sortFields.contains("id")) filters.putIfAbsent("id", text(64));
        filterFields = Map.copyOf(filters); contextParameters = Set.copyOf(contextParameters); }
    public ListQuery parse(Map<String, ? extends List<String>> parameters) {
        for (String key : parameters.keySet()) if (!Set.of("page", "size", "sort").contains(key)
                && !filterFields.containsKey(key) && !contextParameters.contains(key)) throw invalid();
        for (String key : contextParameters) if (parameters.containsKey(key)) scalar(parameters, key);
        int page = integer(parameters, "page", 0), size = integer(parameters, "size", PageRequest.DEFAULT_SIZE);
        var request = new PageRequest(page, size);
        var sorts = new ArrayList<SortOrder>(); var seen = new HashSet<String>();
        var provided = parameters.get("sort");
        if (provided == null) sorts.addAll(defaultSort);
        else {
            if (provided.isEmpty() || provided.size() > 3) throw invalid();
            for (String expression : provided) {
                String[] parts = expression.split(",", -1);
                if (parts.length != 2 || !sortFields.contains(parts[0]) || !seen.add(parts[0])) throw invalid();
                String direction = parts[1].toLowerCase(Locale.ROOT);
                if (!direction.equals("asc") && !direction.equals("desc")) throw invalid();
                sorts.add(new SortOrder(parts[0], direction.equals("desc")));
            }
        }
        if (sorts.stream().noneMatch(s -> s.field().equals("id"))) sorts.add(SortOrder.asc("id"));
        var filters = new LinkedHashMap<String, Object>();
        for (var field : filterFields.entrySet()) if (parameters.containsKey(field.getKey())) {
            String value = scalar(parameters, field.getKey());
            try { filters.put(field.getKey(), field.getValue().apply(value)); }
            catch (RuntimeException failure) { throw invalid(); }
        }
        if (filters.containsKey("from") && filters.containsKey("to")) {
            @SuppressWarnings("unchecked") var from = (Comparable<Object>) filters.get("from");
            if (from.compareTo(filters.get("to")) > 0) throw invalid();
        }
        return new ListQuery(request, sorts, filters, ListScope.GLOBAL);
    }
    private static int integer(Map<String, ? extends List<String>> params, String key, int fallback) {
        if (!params.containsKey(key)) return fallback;
        try { String value = scalar(params, key); if (!value.matches("[0-9]+")) throw invalid(); return Integer.parseInt(value); }
        catch (NumberFormatException failure) { throw invalid(); }
    }
    private static String scalar(Map<String, ? extends List<String>> params, String key) {
        var values = params.get(key);
        if (values == null || values.size() != 1 || values.getFirst() == null || values.getFirst().isBlank()) throw invalid();
        return values.getFirst();
    }
    public static Function<String, Object> text(int maximum) { return value -> {
        if (value.length() > maximum || value.isBlank()) throw invalid(); return value; }; }
    public static Function<String, Object> choices(String... allowed) { var set = Set.of(allowed); return value -> {
        if (!set.contains(value)) throw invalid(); return value; }; }
    public static final Function<String, Object> BOOLEAN = value -> {
        if (!value.equals("true") && !value.equals("false")) throw invalid(); return Boolean.valueOf(value); };
    public static final Function<String, Object> LOCAL_TIME = LocalDateTime::parse;
    public static final Function<String, Object> INSTANT = value -> OffsetDateTime.parse(value).toInstant();
    private static BusinessRuleViolationException invalid() {
        return new BusinessRuleViolationException("Invalid list query; check the documented pagination, filter and sort allowlists");
    }
}
