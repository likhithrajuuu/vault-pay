package com.vaultpay.identity.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Maps to user_oauth_identities. providerSubject must come from verified claims
 * for the configured issuer/audience, not from an untrusted request or email match.
 * Account linking must be authorized; this record does not implement that workflow.
 */
public record OAuthIdentity(
        UUID id,
        UUID userId,
        OAuthProvider provider,
        String providerSubject,
        Instant linkedAt
) {
    public OAuthIdentity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(provider, "provider");
        providerSubject = PostalAddress.requireText(providerSubject, "providerSubject", 255);
        Objects.requireNonNull(linkedAt, "linkedAt");
    }

    @Override
    public String toString() {
        return "OAuthIdentity[id=" + id + ", userId=" + userId + ", provider=" + provider + "]";
    }
}
