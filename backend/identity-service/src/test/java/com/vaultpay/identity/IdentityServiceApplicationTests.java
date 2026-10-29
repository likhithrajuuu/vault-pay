package com.vaultpay.identity;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.vaultpay.identity.auth.LocalAccountRepository;
import com.vaultpay.identity.model.UserStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// DataSource auto-configuration is excluded: no database connection or migration occurs.
// Real Argon2 and real signed tokens exercise the entire security chain; only storage is mocked.
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class IdentityServiceApplicationTests {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "person@example.com";
    private static final String PASSWORD = "test-only-password-123";
    private static final RSAKey KEY;
    private static final Path PRIVATE_KEY;
    private static final Path PUBLIC_KEY;
    private static String passwordHash;

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
            PRIVATE_KEY = Files.createTempFile("vaultpay-test-private-", ".pem");
            PUBLIC_KEY = Files.createTempFile("vaultpay-test-public-", ".pem");
            Files.writeString(PRIVATE_KEY, pem("PRIVATE KEY", KEY.toRSAPrivateKey().getEncoded()));
            Files.writeString(PUBLIC_KEY, pem("PUBLIC KEY", KEY.toRSAPublicKey().getEncoded()));
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vaultpay.security.jwt.private-key-location", () -> PRIVATE_KEY.toUri().toString());
        registry.add("vaultpay.security.jwt.public-key-location", () -> PUBLIC_KEY.toUri().toString());
        registry.add("vaultpay.security.jwt.key-id", () -> "test-key");
    }

    @AfterAll
    static void removeTestKeys() throws Exception {
        Files.deleteIfExists(PRIVATE_KEY);
        Files.deleteIfExists(PUBLIC_KEY);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder passwords;
    @MockitoBean LocalAccountRepository accounts;

    @BeforeEach
    void activeAccount() {
        if (passwordHash == null) {
            passwordHash = passwords.encode(PASSWORD);
        }
        when(accounts.findByEmail(EMAIL)).thenReturn(Optional.of(
                new LocalAccountRepository.LocalAccount(USER_ID, UserStatus.ACTIVE, passwordHash)));
    }

    @Test
    void argon2UsesSchemaCompatibleCostAndRandomSalt() {
        assertThat(passwordHash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(passwords.matches(PASSWORD, passwordHash)).isTrue();
        assertThat(passwords.matches("wrong", passwordHash)).isFalse();
        assertThat(passwords.encode(PASSWORD)).isNotEqualTo(passwordHash);
    }

    @Test
    void loginIssuesRs256AndCanAccessOwnIdentityWithoutSession() throws Exception {
        var login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900)).andReturn();
        String accessToken = mapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
        SignedJWT parsed = SignedJWT.parse(accessToken);
        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(parsed.getHeader().getKeyID()).isEqualTo("test-key");
        assertThat(parsed.getJWTClaimsSet().getSubject()).isEqualTo(USER_ID.toString());
        assertThat(parsed.getJWTClaimsSet().getClaims()).doesNotContainKeys("password", "passwordHash", "email", "pan");
        mvc.perform(get("/api/v1/identity/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.scopes[0]").value("identity:read"))
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void canonicalizesLoginEmail() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("PERSON@EXAMPLE.COM", PASSWORD))).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordUnknownAndInactiveAccountsShareSafeError() throws Exception {
        assertInvalidLogin(PASSWORD + "wrong");
        when(accounts.findByEmail(EMAIL)).thenReturn(Optional.empty());
        assertInvalidLogin(PASSWORD);
        for (var status : new UserStatus[] {UserStatus.PENDING_VERIFICATION, UserStatus.SUSPENDED, UserStatus.CLOSED}) {
            when(accounts.findByEmail(EMAIL)).thenReturn(Optional.of(
                    new LocalAccountRepository.LocalAccount(USER_ID, status, passwordHash)));
            assertInvalidLogin(PASSWORD);
        }
    }

    @Test
    void missingBearerProducesJson401WithoutRedirect() throws Exception {
        mvc.perform(get("/api/v1/identity/me"))
                .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"vaultpay\""))
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void rejectsMalformedExpiredAndWrongIssuerAudienceTokens() throws Exception {
        assertInvalidToken("malformed");
        assertInvalidToken(token(claims -> claims.expirationTime(Date.from(Instant.now().minusSeconds(120)))));
        assertInvalidToken(token(claims -> claims.issuer("https://untrusted.example")));
        assertInvalidToken(token(claims -> claims.audience("another-api")));
        assertInvalidToken(token(claims -> claims.subject("not-a-uuid")));
        assertInvalidToken(token(claims -> claims.expirationTime(null)));
        assertInvalidToken(token(claims -> claims.issueTime(null)));
        assertInvalidToken(token(claims -> claims.jwtID(null)));
        assertInvalidToken(token(claims -> claims.notBeforeTime(Date.from(Instant.now().plusSeconds(300)))));
        assertInvalidToken(token(claims -> claims.issueTime(Date.from(Instant.now().plusSeconds(300)))));
        assertInvalidToken(token(claims -> claims.expirationTime(Date.from(Instant.now().plusSeconds(3600)))));
        assertInvalidToken(token(claims -> claims.claim("scope", 123)));
        assertInvalidToken(token(claims -> claims.claim("scope", java.util.List.of(123))));
    }

    @Test
    void rejectsWrongSignatureAndAlgorithms() throws Exception {
        var foreign = new RSAKeyGenerator(2048).generate();
        var wrongSignature = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims().build());
        wrongSignature.sign(new RSASSASigner(foreign));
        assertInvalidToken(wrongSignature.serialize());
        var rs512 = new SignedJWT(new JWSHeader(JWSAlgorithm.RS512), claims().build());
        rs512.sign(new RSASSASigner(KEY));
        assertInvalidToken(rs512.serialize());
        var hs256 = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims().build());
        hs256.sign(new MACSigner(new byte[32]));
        assertInvalidToken(hs256.serialize());
    }

    @Test
    void authenticatedTokenWithoutRequiredScopeGets403() throws Exception {
        mvc.perform(get("/api/v1/identity/me").header("Authorization", "Bearer "
                        + token(claims -> claims.claim("scope", "another:scope"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_PERMISSIONS"));
    }

    @Test
    void scopeCollectionIsHandledAndMissingScopesAreForbidden() throws Exception {
        mvc.perform(get("/api/v1/identity/me").header("Authorization", "Bearer "
                        + token(claims -> claims.claim("scope", null).claim("scp", java.util.List.of("identity:read")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scopes[0]").value("identity:read"));
        mvc.perform(get("/api/v1/identity/me").header("Authorization", "Bearer "
                        + token(claims -> claims.claim("scope", null))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INSUFFICIENT_PERMISSIONS"));
    }

    @Test
    void malformedBearerIs401AndBasicAuthenticationIsNotAccepted() throws Exception {
        mvc.perform(get("/api/v1/identity/me").header("Authorization", "Bearer has a space"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mvc.perform(get("/api/v1/identity/me").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unsupportedRequestMediaTypeIs415() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("not JSON"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        verifyNoInteractions(accounts);
    }

    @Test
    void unlistedRoutesAreDeniedEvenWithValidToken() throws Exception {
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token(claims -> {})))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_PERMISSIONS"));
    }

    @Test
    void invalidInputNeverEchoesSecretsOrQueriesStorage() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("not-email", "")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("not-email"))));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_JSON"));
        verifyNoInteractions(accounts);
    }

    @Test
    void unavailableDatabaseReturns503WithoutSqlDetails() throws Exception {
        when(accounts.findByEmail(EMAIL)).thenThrow(new DataAccessResourceFailureException("secret database details"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret database"))));
    }

    @Test
    void jwksExposesOnlyPublicRsaParametersAndHealthIsPublic() throws Exception {
        mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kid").value("test-key"))
                .andExpect(jsonPath("$.keys[0].n").exists()).andExpect(jsonPath("$.keys[0].e").exists())
                .andExpect(jsonPath("$.keys[0].d").doesNotExist()).andExpect(jsonPath("$.keys[0].p").doesNotExist())
                .andExpect(jsonPath("$.keys[0].q").doesNotExist());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        verifyNoInteractions(accounts);
    }

    private void assertInvalidLogin(String password) throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(EMAIL, password)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password."))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    private void assertInvalidToken(String token) throws Exception {
        mvc.perform(get("/api/v1/identity/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.detail").value("The access token is invalid or expired."));
    }

    private String loginBody(String email, String password) {
        return mapper.writeValueAsString(java.util.Map.of("email", email, "password", password));
    }

    private String token(Consumer<JWTClaimsSet.Builder> customize) throws Exception {
        var builder = claims();
        customize.accept(builder);
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), builder.build());
        jwt.sign(new RSASSASigner(KEY));
        return jwt.serialize();
    }

    private JWTClaimsSet.Builder claims() {
        var now = Instant.now();
        return new JWTClaimsSet.Builder().issuer("http://localhost:8080").subject(USER_ID.toString())
                .audience("vaultpay-identity").issueTime(Date.from(now)).notBeforeTime(Date.from(now.minusSeconds(1)))
                .expirationTime(Date.from(now.plusSeconds(600))).jwtID(UUID.randomUUID().toString())
                .claim("scope", "identity:read");
    }

    private static String pem(String type, byte[] bytes) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes)
                + "\n-----END " + type + "-----\n";
    }
}
