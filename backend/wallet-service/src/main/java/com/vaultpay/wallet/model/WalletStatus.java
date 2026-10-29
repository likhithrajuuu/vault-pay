package com.vaultpay.wallet.model;

/** Persist using the enum name, never its ordinal. Transition rules belong to application logic. */
public enum WalletStatus {
    /** Metadata exists, but the financial account is not confirmed; the wallet cannot hold or move money. */
    PENDING,
    ACTIVE,
    FROZEN,
    CLOSED
}
