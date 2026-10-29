package com.vaultpay.identity.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Already-encrypted PAN, not a plaintext PAN or an encryption implementation.
 * The future encryption boundary must validate the normalized PAN and produce a
 * versioned authenticated envelope containing the nonce, ciphertext, and tag.
 * keyId references externally managed encryption keys and supports rotation.
 */
public record EncryptedPan(byte[] ciphertext, String keyId) {
    public EncryptedPan {
        Objects.requireNonNull(ciphertext, "ciphertext");
        if (ciphertext.length == 0) {
            throw new IllegalArgumentException("ciphertext must not be empty");
        }
        ciphertext = ciphertext.clone();
        keyId = PostalAddress.requireText(keyId, "keyId", 255);
    }

    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EncryptedPan that
                && keyId.equals(that.keyId)
                && Arrays.equals(ciphertext, that.ciphertext);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(ciphertext) + keyId.hashCode();
    }

    @Override
    public String toString() {
        return "EncryptedPan[REDACTED]";
    }
}
