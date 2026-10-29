package com.vaultpay.identity.auth;

import com.vaultpay.identity.model.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LocalAccountRepository {
    private final JdbcTemplate jdbc;

    public LocalAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<LocalAccount> findByEmail(String canonicalEmail) {
        // Parameter binding prevents login input from becoming executable SQL.
        // OAuth-only users have no password credential and cannot use this endpoint.
        return jdbc.query("""
                SELECT u.id, u.status, c.password_hash
                FROM identity_users u
                JOIN user_password_credentials c ON c.user_id = u.id
                WHERE u.email = ?
                """, (row, index) -> new LocalAccount(row.getObject("id", UUID.class),
                        UserStatus.valueOf(row.getString("status")), row.getString("password_hash")),
                canonicalEmail).stream().findFirst();
    }

    public record LocalAccount(UUID id, UserStatus status, String passwordHash) {
        @Override
        public String toString() {
            return "LocalAccount[id=" + id + ", status=" + status + "]";
        }
    }
}
