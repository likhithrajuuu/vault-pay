package com.vaultpay.wallet;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("vaultpay.wallet")
public record WalletProperties(String defaultCurrency) {
    public WalletProperties {
        if (defaultCurrency == null || !defaultCurrency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Default currency must be a three-letter ISO 4217 code");
        }
    }
}
