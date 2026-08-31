package com.habnut.emulator.rp;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.UUID;

/**
 * RP bank — completely isolated from classic hotel currencies.
 * Only touches habnut_rp_characters.bank_balance and habnut_rp_bank_transactions.
 */
public final class RpBankService {

    private static final Logger log = LoggerFactory.getLogger(RpBankService.class);

    public record TxResult(boolean ok, String reason, int newBalance) {}

    private final DatabaseManager db;
    private final RpCharacterService charService;

    public RpBankService(DatabaseManager db, RpCharacterService charService) {
        this.db = db;
        this.charService = charService;
    }

    public TxResult deposit(long charId, int amount) throws SQLException {
        if (amount <= 0) return new TxResult(false, "invalid_amount", 0);
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                RpCharacterService.Character ch = charService.findById(charId).orElse(null);
                if (ch == null) return rollback(conn, "character_not_found");
                if (ch.cashBalance() < amount) return rollback(conn, "insufficient_cash");

                boolean cashOk = charService.adjustCash(conn, charId, -amount);
                if (!cashOk) return rollback(conn, "insufficient_cash");
                boolean bankOk = charService.adjustBank(conn, charId, amount);
                if (!bankOk) return rollback(conn, "bank_error");

                int newBal = ch.bankBalance() + amount;
                recordTx(conn, charId, null, amount, "deposit", "Cash deposit",
                    ch.bankBalance(), newBal);
                conn.commit();
                return new TxResult(true, null, newBal);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public TxResult withdraw(long charId, int amount) throws SQLException {
        if (amount <= 0) return new TxResult(false, "invalid_amount", 0);
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                RpCharacterService.Character ch = charService.findById(charId).orElse(null);
                if (ch == null) return rollback(conn, "character_not_found");
                if (ch.bankBalance() < amount) return rollback(conn, "insufficient_funds");

                boolean bankOk = charService.adjustBank(conn, charId, -amount);
                if (!bankOk) return rollback(conn, "insufficient_funds");
                charService.adjustCash(conn, charId, amount);

                int newBal = ch.bankBalance() - amount;
                recordTx(conn, charId, null, amount, "withdraw", "Cash withdrawal",
                    ch.bankBalance(), newBal);
                conn.commit();
                return new TxResult(true, null, newBal);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public TxResult transfer(long fromCharId, long toCharId, int amount,
                             String description) throws SQLException {
        if (amount <= 0) return new TxResult(false, "invalid_amount", 0);
        if (fromCharId == toCharId) return new TxResult(false, "self_transfer", 0);
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                RpCharacterService.Character from = charService.findById(fromCharId).orElse(null);
                RpCharacterService.Character to   = charService.findById(toCharId).orElse(null);
                if (from == null || to == null) return rollback(conn, "character_not_found");
                if (from.bankBalance() < amount) return rollback(conn, "insufficient_funds");

                boolean debitOk = charService.adjustBank(conn, fromCharId, -amount);
                if (!debitOk) return rollback(conn, "insufficient_funds");
                charService.adjustBank(conn, toCharId, amount);

                int newBal = from.bankBalance() - amount;
                String desc = description.isBlank() ? "Bank transfer" : description;
                recordTx(conn, fromCharId, toCharId, amount, "transfer", desc,
                    from.bankBalance(), newBal);
                conn.commit();
                return new TxResult(true, null, newBal);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void recordTx(Connection conn, Long fromId, Long toId, int amount,
                           String type, String description,
                           int balBefore, int balAfter) throws SQLException {
        String idempotencyKey = UUID.randomUUID().toString();
        String hash = sha256(idempotencyKey + fromId + toId + amount + type);
        try (PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_rp_bank_transactions" +
                 " (transaction_hash, idempotency_key, from_character_id, to_character_id," +
                 "  amount, type, description, balance_before, balance_after)" +
                 " VALUES (?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, hash);
            ps.setString(2, idempotencyKey);
            if (fromId != null) ps.setLong(3, fromId); else ps.setNull(3, Types.INTEGER);
            if (toId != null) ps.setLong(4, toId); else ps.setNull(4, Types.INTEGER);
            ps.setInt(5, amount);
            ps.setString(6, type);
            ps.setString(7, description);
            ps.setInt(8, balBefore);
            ps.setInt(9, balAfter);
            ps.executeUpdate();
        }
    }

    private TxResult rollback(Connection conn, String reason) {
        try { conn.rollback(); } catch (SQLException e) { log.warn("Rollback failed", e); }
        return new TxResult(false, reason, 0);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
