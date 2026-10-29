package com.vaultpay.identity.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RSAKey signingKey(JwtProperties properties, ResourceLoader resources) {
        if (properties.privateKeyLocation() == null || properties.privateKeyLocation().isBlank()
                || properties.publicKeyLocation() == null || properties.publicKeyLocation().isBlank()) {
            throw new IllegalStateException("External RSA private and public key locations must be configured");
        }
        try (var privateInput = resources.getResource(properties.privateKeyLocation()).getInputStream();
             var publicInput = resources.getResource(properties.publicKeyLocation()).getInputStream()) {
            RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(privateInput);
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(publicInput);
            if (privateKey == null || publicKey == null || publicKey.getModulus().bitLength() < 2048
                    || !privateKey.getModulus().equals(publicKey.getModulus())) {
                throw new IllegalArgumentException("RSA keys must be a matching pair of at least 2048 bits");
            }
            // A signing round trip also catches a mismatched public exponent before serving traffic.
            byte[] probe = "vaultpay-key-pair-check".getBytes(StandardCharsets.US_ASCII);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(probe);
            byte[] signed = signature.sign();
            signature.initVerify(publicKey);
            signature.update(probe);
            if (!signature.verify(signed)) {
                throw new IllegalArgumentException("RSA key pair does not match");
            }
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(properties.keyId()).build();
        } catch (Exception failure) {
            // Do not include key contents in configuration errors.
            throw new IllegalStateException("Unable to load a valid external RSA signing key pair", failure);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey signingKey, JwtProperties properties, Clock clock) throws Exception {
        var decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> {
            try {
                Object scopes = jwt.getClaims().containsKey("scope")
                        ? jwt.getClaims().get("scope") : jwt.getClaims().get("scp");
                // Spring accepts either a scope string or a collection; reject invalid element
                // types here rather than allowing an authority-conversion failure to become a 500.
                if (scopes != null && !(scopes instanceof String)
                        && !(scopes instanceof java.util.Collection<?> values
                             && values.stream().allMatch(String.class::isInstance))) {
                    return invalidClaims();
                }
                if (!UUID.fromString(jwt.getSubject()).toString().equals(jwt.getSubject())
                        || !jwt.getAudience().contains(properties.audience()) || jwt.getExpiresAt() == null
                        || jwt.getIssuedAt() == null || jwt.getId() == null || jwt.getId().isBlank()
                        || jwt.getIssuedAt().isAfter(clock.instant().plusSeconds(60))
                        || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                        || java.time.Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())
                                .compareTo(properties.accessTokenTtl()) > 0) {
                    return invalidClaims();
                }
                return OAuth2TokenValidatorResult.success();
            } catch (RuntimeException invalid) {
                return invalidClaims();
            }
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), requiredClaims));
        return decoder;
    }

    private static OAuth2TokenValidatorResult invalidClaims() {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Required token claims are invalid", null));
    }
}
