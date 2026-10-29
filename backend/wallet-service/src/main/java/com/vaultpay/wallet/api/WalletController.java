package com.vaultpay.wallet.api;

import com.vaultpay.wallet.WalletService;
import com.vaultpay.wallet.model.Wallet;
import com.vaultpay.wallet.model.WalletStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WalletController {
    private final WalletService wallets;

    public WalletController(WalletService wallets) {
        this.wallets = wallets;
    }

    /** No request body: the owner comes from the verified token and the currency from server policy. */
    @PostMapping("/api/v1/wallets")
    public ResponseEntity<WalletResponse> create(JwtAuthenticationToken authentication) {
        var result = wallets.provision(owner(authentication));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .header("Cache-Control", "no-store").body(WalletResponse.from(result.wallet()));
    }

    @GetMapping("/api/v1/wallets/me")
    public ResponseEntity<WalletResponse> mine(JwtAuthenticationToken authentication) {
        return wallets.find(owner(authentication))
                .map(wallet -> ResponseEntity.ok().header("Cache-Control", "no-store")
                        .body(WalletResponse.from(wallet)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static UUID owner(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getToken().getSubject());
    }

    /** Narrow response DTO: the owner id and version are internal details. */
    public record WalletResponse(UUID id, String currency, WalletStatus status, Instant createdAt) {
        static WalletResponse from(Wallet wallet) {
            return new WalletResponse(wallet.id(), wallet.currency(), wallet.status(), wallet.createdAt());
        }
    }
}
