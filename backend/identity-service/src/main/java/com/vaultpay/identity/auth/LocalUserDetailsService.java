package com.vaultpay.identity.auth;

import com.vaultpay.identity.model.UserStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class LocalUserDetailsService implements UserDetailsService {
    private final LocalAccountRepository accounts;

    public LocalUserDetailsService(LocalAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String canonicalEmail) {
        var account = accounts.findByEmail(canonicalEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Local credentials not found"));
        // The principal is the stable UUID, not an email address or an untrusted request field.
        // Ordinary local login never grants operator/admin permissions.
        return User.withUsername(account.id().toString()).password(account.passwordHash())
                .authorities("SCOPE_identity:read")
                .disabled(account.status() != UserStatus.ACTIVE).build();
    }
}
