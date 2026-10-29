package com.vaultpay.identity.auth;

import com.vaultpay.identity.security.JwtProperties;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public AccessToken issue(Authentication authenticated) {
        if (!authenticated.isAuthenticated()) {
            throw new org.springframework.security.authentication.BadCredentialsException("Authentication required");
        }
        var now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer(properties.issuer())
                .subject(authenticated.getName()).audience(List.of(properties.audience()))
                .issuedAt(now).notBefore(now).expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                // Fixed server-owned scope; requests cannot choose their privileges.
                .claim("scope", "identity:read").build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.keyId()).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, "Bearer", properties.accessTokenTtl().toSeconds());
    }

    public record AccessToken(String accessToken, String tokenType, long expiresIn) {
        @Override
        public String toString() {
            return "AccessToken[REDACTED]";
        }
    }
}
