package com.vaultpay.identity.api;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.vaultpay.identity.auth.TokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final TokenService tokens;
    private final RSAKey signingKey;

    public AuthController(AuthenticationManager authenticationManager, TokenService tokens, RSAKey signingKey) {
        this.authenticationManager = authenticationManager;
        this.tokens = tokens;
        this.signingKey = signingKey;
    }

    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<TokenService.AccessToken> login(@Valid @RequestBody LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.email().strip().toLowerCase(Locale.ROOT), request.password()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tokens.issue(authentication));
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> publicKeys() {
        // Only public parameters are exposed. Never serialize the private RSAKey directly.
        return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 1024) String password
    ) {
        @Override
        public String toString() {
            return "LoginRequest[REDACTED]";
        }
    }
}
