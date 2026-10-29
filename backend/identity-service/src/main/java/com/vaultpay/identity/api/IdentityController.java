package com.vaultpay.identity.api;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdentityController {
    @GetMapping("/api/v1/identity/me")
    @PreAuthorize("hasAuthority('SCOPE_identity:read')")
    public CurrentIdentity me(JwtAuthenticationToken authentication) {
        // Return a narrow identity DTO, not User or password/PAN/profile data.
        return new CurrentIdentity(UUID.fromString(authentication.getToken().getSubject()),
                authentication.getAuthorities().stream().map(authority -> authority.getAuthority())
                        .filter(authority -> authority.startsWith("SCOPE_"))
                        .map(authority -> authority.substring("SCOPE_".length())).sorted().toList());
    }

    public record CurrentIdentity(UUID userId, List<String> scopes) { }
}
