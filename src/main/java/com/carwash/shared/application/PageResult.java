package com.carwash.shared.application;

import java.util.List;
import java.util.function.Function;

/** One authorized snapshot, including totals for empty and out-of-range pages. */
public record PageResult<T>(List<T> content, int page, int size, long totalElements,
                            long totalPages, boolean hasNext, boolean hasPrevious) {
    public PageResult { content = List.copyOf(content); }
    public static <T> PageResult<T> of(List<T> content, PageRequest request, long total) {
        if (total < 0 || content.size() > request.size()) throw new IllegalArgumentException("Invalid page result");
        long pages = total / request.size() + (total % request.size() == 0 ? 0 : 1);
        return new PageResult<>(content, request.page(), request.size(), total, pages,
                (long) request.page() + 1 < pages, request.page() > 0);
    }
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = content.stream().map(mapper).map(v -> (R) v).toList();
        return new PageResult<>(mapped, page, size, totalElements, totalPages, hasNext, hasPrevious);
    }
    /** Only for already calculated/ranked derived results, or the in-memory adapter. */
    public static <T> PageResult<T> select(List<T> ranked, PageRequest request) {
        int start = Math.min(request.offset(), ranked.size());
        int end = (int) Math.min((long) start + request.size(), ranked.size());
        return of(ranked.subList(start, end), request, ranked.size());
    }
}
