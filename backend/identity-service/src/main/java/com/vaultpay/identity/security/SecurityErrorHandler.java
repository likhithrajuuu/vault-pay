package com.vaultpay.identity.security;

import com.vaultpay.identity.api.ApiProblem;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
                         AuthenticationException failure) throws IOException, ServletException {
        boolean invalidToken = failure instanceof OAuth2AuthenticationException;
        boolean invalidRequest = invalidToken && "invalid_request".equals(
                ((OAuth2AuthenticationException) failure).getError().getErrorCode());
        var status = invalidRequest ? HttpStatus.BAD_REQUEST : HttpStatus.UNAUTHORIZED;
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"vaultpay\""
                + (invalidToken ? ", error=\"" + (invalidRequest ? "invalid_request" : "invalid_token") + "\"" : ""));
        write(request, response, status,
                invalidRequest ? "INVALID_AUTHORIZATION_HEADER" : invalidToken ? "INVALID_TOKEN" : "AUTHENTICATION_REQUIRED",
                invalidRequest ? "The bearer authorization header is malformed."
                        : invalidToken ? "The access token is invalid or expired." : "A bearer access token is required.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       org.springframework.security.access.AccessDeniedException failure) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                "Bearer realm=\"vaultpay\", error=\"insufficient_scope\"");
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
