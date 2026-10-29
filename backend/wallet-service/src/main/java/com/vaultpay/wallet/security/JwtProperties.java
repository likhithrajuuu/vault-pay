package com.vaultpay.wallet.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("vaultpay.security.jwt")
public record JwtProperties(String issuer, String audience, String jwkSetUri) {
    public JwtProperties {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()
                || jwkSetUri == null || jwkSetUri.isBlank()) {
            throw new IllegalArgumentException("JWT issuer, audience, and JWK set URI are required");
        }
    }
}
