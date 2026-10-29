package com.vaultpay.wallet.model;

import java.time.Instant;
import java.util.UUID;

/** Metadata only. There is intentionally no balance: Transfer & Ledger is the sole balance authority. */
public record Wallet(
        UUID id, 
        UUID ownerUserId, 
        String currency, 
        WalletStatus status,
        Instant createdAt, 
        Instant updatedAt, 
        long version
) { }
