package com.vaultpay.identity.model;

/** Persist using the enum name, never its ordinal. Transition rules belong to application logic. */
public enum UserStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    CLOSED
}
