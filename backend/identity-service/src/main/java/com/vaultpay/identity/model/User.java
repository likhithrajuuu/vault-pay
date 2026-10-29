package com.vaultpay.identity.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistence-neutral identity model for identity_users; not an API response DTO.
 * IDs are application-generated UUIDs (for example UUID.randomUUID()).
 * Address, phone, and PAN can be absent during registration, including OAuth signup.
 * Password hashes and provider identities are separate models with separate tables.
 */
public record User(
        UUID id,
        String email,
        String fullName,
        String phoneNumber,
        UserStatus status,
        Instant emailVerifiedAt,
        Instant phoneVerifiedAt,
        PostalAddress address,
        EncryptedPan pan,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public User {
        Objects.requireNonNull(id, "id");
        email = PostalAddress.requireText(email, "email", 254);
        if (!email.equals(email.strip().toLowerCase(java.util.Locale.ROOT))
                || !email.matches("[^\\s@]+@[^\\s@]+")) {
            throw new IllegalArgumentException("email must be canonical lowercase and have an email shape");
        }
        fullName = PostalAddress.requireText(fullName, "fullName", 200);
        if (phoneNumber != null && !phoneNumber.matches("\\+[1-9][0-9]{1,14}")) {
            throw new IllegalArgumentException("phoneNumber must use E.164 format");
        }
        if (phoneVerifiedAt != null && phoneNumber == null) {
            throw new IllegalArgumentException("phone verification requires a phone number");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt) || version < 0) {
            throw new IllegalArgumentException("invalid timestamps or version");
        }
    }

    // Future write paths must clear verification when the corresponding contact changes.
    // A version field only enables optimistic locking when persistence enforces it.
    @Override
    public String toString() {
        return "User[id=" + id + ", status=" + status + "]";
    }
}
