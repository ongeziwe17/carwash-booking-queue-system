package com.carwash.shared.application;

import java.util.*;
import java.util.function.Function;

public record ListQuery(PageRequest page, List<SortOrder> sort, Map<String, Object> filters, ListScope scope) {
    public ListQuery { sort = List.copyOf(sort); filters = Map.copyOf(filters); Objects.requireNonNull(scope); }
    public ListQuery scoped(ListScope authorized) { return new ListQuery(page, sort, filters, authorized); }
    /** Called only by an owned in-memory adapter after its authorization predicate. */
    public <T> PageResult<T> select(List<T> authorized, Map<String, Function<T, ?>> fields) {
        Comparator<T> order = (a, b) -> 0;
        for (var term : sort) {
            var field = Objects.requireNonNull(fields.get(term.field()), "Unmapped sort field");
            order = order.thenComparing((a, b) -> compare(field.apply(a), field.apply(b), term.descending()));
        }
        var matching = authorized.stream().filter(row -> filters.entrySet().stream().allMatch(filter -> {
            Object value = Objects.requireNonNull(fields.get(filter.getKey()), "Unmapped filter field").apply(row);
            if (value == null) return false;
            int comparison = compare(value, filter.getValue(), false);
            return filter.getKey().equals("from") ? comparison >= 0
                    : filter.getKey().equals("to") ? comparison <= 0 : comparison == 0;
        })).sorted(order).toList();
        return PageResult.select(matching, page);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(Object a, Object b, boolean descending) {
        if (a == null || b == null) return a == b ? 0 : a == null ? 1 : -1; // NULLS LAST in either direction
        int result;
        if (a instanceof Enum<?> e) a = e.name();
        if (b instanceof Enum<?> e) b = e.name();
        if (a instanceof String x && b instanceof String y) {
            // PostgreSQL C collation compares Unicode code points, not the host locale.
            var xi = x.codePoints().iterator(); var yi = y.codePoints().iterator(); result = 0;
            while (xi.hasNext() && yi.hasNext() && result == 0) result = Integer.compare(xi.nextInt(), yi.nextInt());
            if (result == 0) result = xi.hasNext() ? 1 : yi.hasNext() ? -1 : 0;
        } else result = ((Comparable) a).compareTo(b);
        return descending ? -Integer.signum(result) : Integer.signum(result);
    }
}
