package com.vaultpay.identity.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiProblem> validation(MethodArgumentNotValidException failure, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        // Return field names and validation messages, never rejected passwords or tokens.
        failure.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more request fields are invalid.", request, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiProblem> unreadable(HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_JSON", "The request body must contain valid JSON.", request, Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiProblem> authentication(HttpServletRequest request) {
        // Disabled, unknown, OAuth-only, and incorrect-password accounts have the same message.
        return problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password.", request, Map.of());
    }

    @ExceptionHandler({InternalAuthenticationServiceException.class, DataAccessException.class})
    ResponseEntity<ApiProblem> storage(HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "AUTHENTICATION_UNAVAILABLE",
                "Authentication is temporarily unavailable. Please try again later.", request, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiProblem> forbidden(HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "INSUFFICIENT_PERMISSIONS",
                "You do not have permission to perform this operation.", request, Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiProblem> method(HttpServletRequest request) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "This HTTP method is not supported.", request, Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiProblem> unsupportedMedia(HttpServletRequest request) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Use application/json for this request.", request, Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ApiProblem> unacceptableMedia(HttpServletRequest request) {
        return problem(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE",
                "The requested response format is not supported.", request, Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiProblem> missing(HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found.", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiProblem> unexpected(Exception failure, HttpServletRequest request) {
        LOG.error("Unexpected identity API failure", failure);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred.", request, Map.of());
    }

    private ResponseEntity<ApiProblem> problem(HttpStatus status, String code, String detail,
                                               HttpServletRequest request, Map<String, String> fields) {
        var response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (status == HttpStatus.UNAUTHORIZED) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"vaultpay\"");
        }
        return response.body(ApiProblem.of(status, code, detail, request, fields));
    }
}
