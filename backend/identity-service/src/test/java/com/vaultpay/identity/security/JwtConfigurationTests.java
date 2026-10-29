package com.vaultpay.identity.security;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigurationTests {
    @Test
    void missingExternalKeysFailClosed() {
        assertThatThrownBy(() -> new JwtConfiguration().signingKey(
                properties("", ""), new DefaultResourceLoader()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("must be configured");
    }

    @Test
    void mismatchedKeysAreRejected() throws Exception {
        var first = new RSAKeyGenerator(2048).generate();
        var second = new RSAKeyGenerator(2048).generate();
        assertThatThrownBy(() -> new JwtConfiguration().signingKey(properties("private", "public"),
                resources(first.toRSAPrivateKey().getEncoded(), second.toRSAPublicKey().getEncoded())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("valid external RSA");
    }

    @Test
    void weakKeysAreRejected() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        var pair = generator.generateKeyPair();
        assertThatThrownBy(() -> new JwtConfiguration().signingKey(properties("private", "public"),
                resources(pair.getPrivate().getEncoded(), pair.getPublic().getEncoded())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("valid external RSA");
    }

    @Test
    void invalidTokenLifetimesAreRejected() {
        assertThatThrownBy(() -> new JwtProperties("issuer", "audience", Duration.ZERO, "kid", "private", "public"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties("issuer", "audience", Duration.ofHours(1), "kid", "private", "public"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private JwtProperties properties(String privateLocation, String publicLocation) {
        return new JwtProperties("issuer", "audience", Duration.ofMinutes(15), "kid", privateLocation, publicLocation);
    }

    private DefaultResourceLoader resources(byte[] privateBytes, byte[] publicBytes) {
        return new DefaultResourceLoader() {
            @Override
            public Resource getResource(String location) {
                String type = location.equals("private") ? "PRIVATE KEY" : "PUBLIC KEY";
                byte[] value = location.equals("private") ? privateBytes : publicBytes;
                String pem = "-----BEGIN " + type + "-----\n" + Base64.getEncoder().encodeToString(value)
                        + "\n-----END " + type + "-----\n";
                return new ByteArrayResource(pem.getBytes(StandardCharsets.US_ASCII));
            }
        };
    }
}
