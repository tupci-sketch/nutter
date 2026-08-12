package com.habnut.emulator.economy;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.sql.*;
import java.util.HexFormat;
import java.util.UUID;

public final class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    public enum Currency { CREDITS, DIAMONDS, NUT_POINTS, SEASONAL }

    public record Balance(long credits, long diamonds, long nutPoints, long seasonal) {}

    private final DatabaseManager db;

    public TransactionService(DatabaseManager db) {
        this.db = db;
    }

    public Balance getBalance(long userId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT credits, diamonds, nut_points, seasonal_currency " +
                 "FROM habnut_users WHERE id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new Balance(0, 0, 0, 0);
                return new Balance(rs.getLong("credits"), rs.getLong("diamonds"),
                    rs.getLong("nut_points"), rs.getLong("seasonal_currency"));
            }
        } catch (SQLException e) {
            log.error("getBalance failed for user {}", userId, e);
            return new Balance(0, 0, 0, 0);
        }
    }

    public void grant(long userId, Currency currency, long amount,
                      String reason, String idempotencyKey) throws SQLException {
        if (amount <= 0) throw new IllegalArgumentException("Grant amount must be positive");
        executeTransaction(userId, currency, amount, reason,
            idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString());
    }

    public void debit(long userId, Currency currency, long amount,
                      String reason, String idempotencyKey) throws SQLException {
        if (amount <= 0) throw new IllegalArgumentException("Debit amount must be positive");
        executeTransaction(userId, currency, -amount, reason,
            idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString());
    }

    private void executeTransaction(long userId, Currency currency, long delta,
                                    String reason, String idempotencyKey) throws SQLException {
        String column = currencyColumn(currency);
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            // Lock the user row
            try (PreparedStatement lock = conn.prepareStatement(
                "SELECT " + column + " FROM habnut_users WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, userId);
                try (ResultSet rs = lock.executeQuery()) {
                    if (!rs.next()) throw new SQLException("User not found: " + userId);
                    long current = rs.getLong(column);
                    if (delta < 0 && current + delta < 0) {
                        conn.rollback();
                        throw new IllegalStateException("Insufficient balance");
                    }
                }
            }
            // Insert immutable transaction row
            String hash = buildHash(userId, currency, delta, idempotencyKey);
            try (PreparedStatement ins = conn.prepareStatement(
                "INSERT IGNORE INTO habnut_transactions " +
                "(user_id, currency, amount, reason, idempotency_key, transaction_hash) " +
                "VALUES (?, ?, ?, ?, ?, ?)")) {
                ins.setLong(1, userId);
                ins.setString(2, currency.name().toLowerCase());
                ins.setLong(3, delta);
                ins.setString(4, reason);
                ins.setString(5, idempotencyKey);
                ins.setString(6, hash);
                int rows = ins.executeUpdate();
                if (rows == 0) {
                    // Idempotency: already processed
                    conn.rollback();
                    return;
                }
            }
            // Apply delta
            try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE habnut_users SET " + column + " = " + column + " + ? WHERE id = ?")) {
                upd.setLong(1, delta);
                upd.setLong(2, userId);
                upd.executeUpdate();
            }
            conn.commit();
            log.debug("Transaction: user={} currency={} delta={} reason={}", userId, currency, delta, reason);
        }
    }

    private String currencyColumn(Currency c) {
        return switch (c) {
            case CREDITS   -> "credits";
            case DIAMONDS  -> "diamonds";
            case NUT_POINTS-> "nut_points";
            case SEASONAL  -> "seasonal_currency";
        };
    }

    private String buildHash(long userId, Currency currency, long amount, String idempotencyKey) {
        try {
            String input = userId + "|" + currency + "|" + amount + "|" + idempotencyKey;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
}
