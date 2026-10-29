package com.vaultpay.identity.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("vaultpay.security.jwt")
public record JwtProperties(
        String issuer, String audience, Duration accessTokenTtl, String keyId,
        String privateKeyLocation, String publicKeyLocation
) {
    public JwtProperties {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()
                || keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("JWT issuer, audience, and key ID are required");
        }
        if (accessTokenTtl == null || accessTokenTtl.compareTo(Duration.ofSeconds(1)) < 0
                || accessTokenTtl.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException("Access token lifetime must be between one second and fifteen minutes");
        }
    }
}
