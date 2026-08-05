package com.carwash.api.error;

import com.carwash.api.dto.ApiErrorResponse;
import com.carwash.api.dto.ApiFieldError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Component
public class ApiErrorResponseFactory {

    private static final Comparator<ApiFieldError> FIELD_ERROR_ORDER = Comparator
            .comparing(ApiFieldError::field, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ApiFieldError::message, Comparator.nullsLast(Comparator.naturalOrder()));

    private final Clock clock;

    public ApiErrorResponseFactory(Clock clock) {
        this.clock = clock;
    }

    public ApiErrorResponse create(
            HttpStatusCode status,
            ApiErrorCode code,
            String message,
            HttpServletRequest request
    ) {
        return create(status, code, message, request.getRequestURI(), List.of());
    }

    public ApiErrorResponse create(
            HttpStatusCode status,
            ApiErrorCode code,
            String message,
            HttpServletRequest request,
            List<ApiFieldError> fieldErrors
    ) {
        return create(status, code, message, request.getRequestURI(), fieldErrors);
    }

    public ApiErrorResponse create(
            HttpStatusCode status,
            ApiErrorCode code,
            String message,
            String path,
            List<ApiFieldError> fieldErrors
    ) {
        List<ApiFieldError> safeFieldErrors = fieldErrors == null
                ? List.of()
                : fieldErrors.stream().sorted(FIELD_ERROR_ORDER).toList();
        return new ApiErrorResponse(status.value(), code.name(), message, Instant.now(clock), path, safeFieldErrors);
    }
}
