package com.vaultpay.identity.model;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Optional local credential for user_password_credentials; no row for OAuth-only users.
 * Accepts an already encoded, bare Argon2id PHC string, not a raw password.
 * This model does not hash or verify passwords. A real encoder must supply random
 * per-password salts and be benchmarked before authentication is implemented.
 * Baseline: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
 */
public record PasswordCredential(
        UUID userId,
        String passwordHash,
        Instant passwordChangedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    private static final Pattern PHC = Pattern.compile(
            "\\$argon2id\\$v=19\\$m=([1-9][0-9]*),t=([1-9][0-9]*),p=([1-9][0-9]*)"
                    + "\\$[A-Za-z0-9+/]+\\$[A-Za-z0-9+/]+"
    );

    public PasswordCredential {
        Objects.requireNonNull(userId, "userId");
        passwordHash = PostalAddress.requireText(passwordHash, "passwordHash", 1024);
        var match = PHC.matcher(passwordHash);
        if (!match.matches()
                || new BigInteger(match.group(1)).compareTo(BigInteger.valueOf(19456)) < 0
                || new BigInteger(match.group(2)).compareTo(BigInteger.TWO) < 0
                || new BigInteger(match.group(3)).signum() <= 0) {
            throw new IllegalArgumentException("passwordHash must be Argon2id v19 with m>=19456, t>=2, p>=1");
        }
        Objects.requireNonNull(passwordChangedAt, "passwordChangedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt) || passwordChangedAt.isBefore(createdAt)
                || passwordChangedAt.isAfter(updatedAt) || version < 0) {
            throw new IllegalArgumentException("invalid credential timestamps or version");
        }
    }

    @Override
    public String toString() {
        return "PasswordCredential[userId=" + userId + ", passwordHash=REDACTED]";
    }
}
