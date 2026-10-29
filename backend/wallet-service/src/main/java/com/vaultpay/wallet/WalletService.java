package com.vaultpay.wallet;

import com.vaultpay.wallet.model.Wallet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WalletService {
    private final WalletRepository wallets;
    private final WalletProperties properties;

    public WalletService(WalletRepository wallets, WalletProperties properties) {
        this.wallets = wallets;
        this.properties = properties;
    }

    /** Creates the caller's wallet, or returns the existing one. Safe to retry. */
    public Provisioned provision(UUID ownerUserId) {
        return wallets.insertIfAbsent(UUID.randomUUID(), ownerUserId, properties.defaultCurrency())
                .map(wallet -> new Provisioned(wallet, true))
                .orElseGet(() -> new Provisioned(wallets.findByOwner(ownerUserId).orElseThrow(), false));
    }

    public Optional<Wallet> find(UUID ownerUserId) {
        return wallets.findByOwner(ownerUserId);
    }

    public record Provisioned(Wallet wallet, boolean created) { }
}
