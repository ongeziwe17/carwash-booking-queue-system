package com.carwash.api;

import com.carwash.api.dto.ApiErrorResponse;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ResourceNotFoundException ex,
            ServletWebRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(
                        404,
                        ex.getMessage(),
                        LocalDateTime.now().toString(),
                        request.getRequest().getRequestURI()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            ServletWebRequest request
    ) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));

        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        400,
                        "Validation failed: " + message,
                        LocalDateTime.now().toString(),
                        request.getRequest().getRequestURI()
                ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestParameter(
            MissingServletRequestParameterException ex,
            ServletWebRequest request
    ) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        400,
                        "Required request parameter '" + ex.getParameterName() + "' is missing",
                        LocalDateTime.now().toString(),
                        request.getRequest().getRequestURI()
                ));
    }

    @ExceptionHandler({BusinessRuleViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
            Exception ex,
            ServletWebRequest request
    ) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        400,
                        ex.getMessage(),
                        LocalDateTime.now().toString(),
                        request.getRequest().getRequestURI()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleInternalServerError(
            Exception ex,
            ServletWebRequest request
    ) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        500,
                        ex.getMessage(),
                        LocalDateTime.now().toString(),
                        request.getRequest().getRequestURI()
                ));
    }
}