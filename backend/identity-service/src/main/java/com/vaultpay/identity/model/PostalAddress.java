package com.vaultpay.identity.model;

import java.util.Objects;

/** Optional on a user; once supplied, all components except line2 are required. */
public record PostalAddress(
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String countryCode
) {
    public PostalAddress {
        line1 = requireText(line1, "line1", 200);
        if (line2 != null) {
            line2 = requireText(line2, "line2", 200);
        }
        city = requireText(city, "city", 100);
        state = requireText(state, "state", 100);
        postalCode = requireText(postalCode, "postalCode", 20);
        Objects.requireNonNull(countryCode, "countryCode");
        if (!countryCode.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("countryCode must be two uppercase letters");
        }
    }

    static String requireText(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException(field + " must be nonblank and within its length limit");
        }
        return value;
    }

    @Override
    public String toString() {
        return "PostalAddress[REDACTED]";
    }
}
