package com.carwash.api;

import com.carwash.api.dto.ApiErrorResponse;
import com.carwash.api.dto.ApiFieldError;
import com.carwash.api.error.ApiErrorCode;
import com.carwash.api.error.ApiErrorResponseFactory;
import com.carwash.security.InvalidCredentialsException;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String VALIDATION_MESSAGE = "Request validation failed";

    private final ApiErrorResponseFactory errors;

    public ApiExceptionHandler(ApiErrorResponseFactory errors) {
        this.errors = errors;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, ServletWebRequest request) {
        return response(HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED, "Access denied", request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex,
            ServletWebRequest request
    ) {
        return response(HttpStatus.UNAUTHORIZED, ApiErrorCode.INVALID_CREDENTIALS,
                InvalidCredentialsException.SAFE_MESSAGE, request);
    }

    @ExceptionHandler({ResourceNotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ResourceNotFoundException ex,
            ServletWebRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUnknownResource(
            NoResourceFoundException ex,
            ServletWebRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Resource not found", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            ServletWebRequest request
    ) {
        List<ApiFieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::safeFieldError)
                .toList();
        return validationResponse(fieldErrors, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(
            HandlerMethodValidationException ex,
            ServletWebRequest request
    ) {
        List<ApiFieldError> fieldErrors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String parameterName = result.getMethodParameter().getParameterName();
            String safeName = parameterName == null ? "parameter" : parameterName;
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors().forEach(error -> fieldErrors.add(safeFieldError(error)));
            } else {
                result.getResolvableErrors().forEach(error -> fieldErrors.add(
                        new ApiFieldError(safeName, safeMessage(error.getDefaultMessage()))));
            }
        });
        return validationResponse(fieldErrors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolations(
            ConstraintViolationException ex,
            ServletWebRequest request
    ) {
        List<ApiFieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(
                        lastPathElement(violation.getPropertyPath().toString()),
                        safeMessage(violation.getMessage())))
                .toList();
        return validationResponse(fieldErrors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedRequestBody(
            HttpMessageNotReadableException ex,
            ServletWebRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, ApiErrorCode.MALFORMED_REQUEST,
                "Malformed or invalid request body", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            ServletWebRequest request
    ) {
        String name = ex.getName() == null ? "parameter" : ex.getName();
        return response(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_PARAMETER,
                "Invalid value for parameter '" + name + "'", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestParameter(
            MissingServletRequestParameterException ex,
            ServletWebRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, ApiErrorCode.MISSING_PARAMETER,
                "Required parameter '" + ex.getParameterName() + "' is missing", request);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessRuleViolation(
            BusinessRuleViolationException ex,
            ServletWebRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, ApiErrorCode.BUSINESS_RULE_VIOLATION,
                ex.getMessage(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            ServletWebRequest request
    ) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        String[] supportedMethods = ex.getSupportedMethods();
        if (supportedMethods != null && supportedMethods.length > 0) {
            response.header(HttpHeaders.ALLOW, String.join(", ", supportedMethods));
        }
        return response.body(errors.create(
                HttpStatus.METHOD_NOT_ALLOWED,
                ApiErrorCode.METHOD_NOT_ALLOWED,
                "HTTP method is not supported",
                request.getRequest()
        ));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex,
            ServletWebRequest request
    ) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .headers(ex.getHeaders())
                .body(errors.create(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        ApiErrorCode.UNSUPPORTED_MEDIA_TYPE,
                        "Content type is not supported",
                        request.getRequest()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleInternalServerError(
            Exception ex,
            ServletWebRequest request
    ) {
        log.error("Unexpected API failure for {} {}", request.getRequest().getMethod(),
                request.getRequest().getRequestURI(), ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiErrorResponse> validationResponse(
            List<ApiFieldError> fieldErrors,
            ServletWebRequest request
    ) {
        return ResponseEntity.badRequest().body(errors.create(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_FAILED,
                VALIDATION_MESSAGE,
                request.getRequest(),
                fieldErrors
        ));
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            ApiErrorCode code,
            String message,
            ServletWebRequest request
    ) {
        return ResponseEntity.status(status).body(errors.create(status, code, message, request.getRequest()));
    }

    private ApiFieldError safeFieldError(FieldError error) {
        return new ApiFieldError(error.getField(), safeMessage(error.getDefaultMessage()));
    }

    private String safeMessage(String message) {
        return message == null || message.isBlank() ? "is invalid" : message;
    }

    private String lastPathElement(String path) {
        int separator = path.lastIndexOf('.');
        return separator < 0 ? path : path.substring(separator + 1);
    }
}
