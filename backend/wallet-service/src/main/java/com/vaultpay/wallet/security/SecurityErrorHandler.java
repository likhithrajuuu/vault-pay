package com.vaultpay.wallet.security;

import com.vaultpay.wallet.api.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper mapper;

    public SecurityErrorHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException failure) throws IOException {
        boolean invalidToken = failure instanceof OAuth2AuthenticationException;
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                "Bearer realm=\"vaultpay\"" + (invalidToken ? ", error=\"invalid_token\"" : ""));
        write(request, response, HttpStatus.UNAUTHORIZED,
                invalidToken ? "INVALID_TOKEN" : "AUTHENTICATION_REQUIRED",
                invalidToken ? "The access token is invalid or expired." : "A bearer access token is required.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException failure) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "INSUFFICIENT_PERMISSIONS",
                "You do not have permission to perform this operation.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       HttpStatus status, String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        // Never serialize authentication exceptions: they can disclose claims or provider details.
        mapper.writeValue(response.getOutputStream(), ApiProblem.of(status, code, detail, request, Map.of()));
    }
}
