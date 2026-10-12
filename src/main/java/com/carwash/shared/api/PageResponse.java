package com.carwash.shared.api;

import com.carwash.shared.application.PageResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Bounded zero-based page from one authorized snapshot. Empty pages retain requested page and matching totals.")
public record PageResponse<T>(List<T> content, int page, int size, long totalElements,
                              long totalPages, boolean hasNext, boolean hasPrevious) {
    public PageResponse { content = List.copyOf(content); }
    public static <T> PageResponse<T> from(PageResult<T> result) {
        return new PageResponse<>(result.content(), result.page(), result.size(), result.totalElements(),
                result.totalPages(), result.hasNext(), result.hasPrevious());
    }
}
