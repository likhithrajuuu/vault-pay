package com.vaultpay.wallet;

import com.vaultpay.wallet.model.Wallet;
import com.vaultpay.wallet.model.WalletStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class WalletRepository {
    private static final String COLUMNS =
            "id, owner_user_id, currency, status, created_at, updated_at, version";
    private static final RowMapper<Wallet> MAPPER = WalletRepository::map;

    private final JdbcTemplate jdbc;

    public WalletRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts the wallet unless the owner already has one. The unique constraint decides the winner,
     * so two concurrent requests for the same user cannot both insert.
     *
     * @return the new wallet, or empty when the owner already had one
     */
    public Optional<Wallet> insertIfAbsent(UUID id, UUID ownerUserId, String currency) {
        return jdbc.query("""
                INSERT INTO wallets (id, owner_user_id, currency, status)
                VALUES (?, ?, ?, 'PENDING')
                ON CONFLICT (owner_user_id) DO NOTHING
                RETURNING """ + " " + COLUMNS, MAPPER, id, ownerUserId, currency).stream().findFirst();
    }

    public Optional<Wallet> findByOwner(UUID ownerUserId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM wallets WHERE owner_user_id = ?",
                MAPPER, ownerUserId).stream().findFirst();
    }

    private static Wallet map(ResultSet row, int index) throws SQLException {
        return new Wallet(row.getObject("id", UUID.class), row.getObject("owner_user_id", UUID.class),
                row.getString("currency"), WalletStatus.valueOf(row.getString("status")),
                row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant(),
                row.getLong("version"));
    }
}
