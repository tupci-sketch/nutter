package com.habnut.emulator.auth;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class BanService {

    private static final Logger log = LoggerFactory.getLogger(BanService.class);

    private final DatabaseManager db;

    public BanService(DatabaseManager db) {
        this.db = db;
    }

    public record ActiveBan(String reason, String expiresAt) {}

    public ActiveBan getActiveBan(long userId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT reason, expires_at FROM habnut_bans " +
                 "WHERE user_id = ? AND (expires_at IS NULL OR expires_at > NOW()) " +
                 "AND lifted_at IS NULL ORDER BY created_at DESC LIMIT 1")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String expiresAt = rs.getTimestamp("expires_at") != null
                        ? rs.getTimestamp("expires_at").toInstant().toString() : null;
                    return new ActiveBan(rs.getString("reason"), expiresAt);
                }
            }
        } catch (SQLException e) {
            log.warn("Ban check failed for user {}", userId, e);
        }
        return null;
    }

    public boolean isBanned(long userId) {
        return getActiveBan(userId) != null;
    }

    public void ban(long userId, String banType, String reason, String expiresAt,
                    long issuedBy) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_bans (user_id, ban_type, reason, expires_at, issued_by_id) " +
                 "VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, userId);
            ps.setString(2, banType);
            ps.setString(3, reason);
            if (expiresAt != null) {
                ps.setString(4, expiresAt);
            } else {
                ps.setNull(4, java.sql.Types.TIMESTAMP);
            }
            ps.setLong(5, issuedBy);
            ps.executeUpdate();
        }
    }

    public void revoke(long banId, long revokedBy) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_bans SET lifted_at = NOW(), lifted_by_id = ? WHERE id = ?")) {
            ps.setLong(1, revokedBy);
            ps.setLong(2, banId);
            ps.executeUpdate();
        }
    }
}
