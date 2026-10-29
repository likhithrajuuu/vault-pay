package com.vaultpay.wallet.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiProblem> storage(DataAccessException failure, HttpServletRequest request) {
        LOG.error("Wallet storage failure", failure);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "WALLET_UNAVAILABLE",
                "Wallets are temporarily unavailable. Please try again later.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiProblem> forbidden(HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "INSUFFICIENT_PERMISSIONS",
                "You do not have permission to perform this operation.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiProblem> method(HttpServletRequest request) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "This HTTP method is not supported.", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiProblem> missing(HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found.", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiProblem> unexpected(Exception failure, HttpServletRequest request) {
        LOG.error("Unexpected wallet API failure", failure);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred.", request);
    }

    private ResponseEntity<ApiProblem> problem(HttpStatus status, String code, String detail,
                                               HttpServletRequest request) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiProblem.of(status, code, detail, request, Map.of()));
    }
}
