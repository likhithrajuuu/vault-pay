package com.vaultpay.identity.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Shared shape for MVC errors and failures raised before controllers in the security chain. */
public record ApiProblem(
        String type, String title, int status, String detail, String instance,
        String code, String timestamp, Map<String, String> fieldErrors
) {
    public static ApiProblem of(HttpStatus status, String code, String detail,
                                HttpServletRequest request, Map<String, String> fields) {
        // Request URI deliberately excludes the query string, which could contain sensitive data.
        return new ApiProblem("about:blank", status.getReasonPhrase(), status.value(), detail,
                request.getRequestURI(), code, Instant.now().toString(), Map.copyOf(fields));
    }
}
