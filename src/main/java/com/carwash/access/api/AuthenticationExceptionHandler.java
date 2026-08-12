package com.carwash.access.api;

import com.carwash.access.application.InvalidCredentialsException;
import com.carwash.shared.api.error.ApiErrorCode;
import com.carwash.shared.api.error.ApiErrorResponse;
import com.carwash.shared.api.error.ApiErrorResponseFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AuthenticationController.class)
public class AuthenticationExceptionHandler {

    private final ApiErrorResponseFactory errors;

    public AuthenticationExceptionHandler(ApiErrorResponseFactory errors) {
        this.errors = errors;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException exception,
            ServletWebRequest request
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errors.create(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCode.INVALID_CREDENTIALS,
                InvalidCredentialsException.SAFE_MESSAGE,
                request.getRequest()
        ));
    }
}
