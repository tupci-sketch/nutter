package com.habnut.emulator.auth;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.sql.*;
import java.time.Instant;

public final class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);

    public record UserRow(
        long id,
        String username,
        String email,
        String passwordHash,
        String figureString,
        int rank,
        long credits,
        long diamonds,
        long nutPoints,
        boolean emailVerified,
        Instant lastLogin,
        String lastIp
    ) {}

    private final DatabaseManager db;

    public UserRepository(DatabaseManager db) {
        this.db = db;
    }

    public UserRow findByUsername(String username) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, username, email, password_hash, figure, rank, " +
                 "credits, diamonds, nut_points, email_verified, last_login, last_ip " +
                 "FROM habnut_users WHERE username = ? LIMIT 1")) {
            ps.setString(1, username);
            return mapFirst(ps);
        } catch (SQLException e) {
            log.error("findByUsername failed for '{}'", username, e);
            return null;
        }
    }

    public UserRow findById(long id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, username, email, password_hash, figure, rank, " +
                 "credits, diamonds, nut_points, email_verified, last_login, last_ip " +
                 "FROM habnut_users WHERE id = ? LIMIT 1")) {
            ps.setLong(1, id);
            return mapFirst(ps);
        } catch (SQLException e) {
            log.error("findById failed for {}", id, e);
            return null;
        }
    }

    public void updateLastLogin(long userId, String ipAddress) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_users SET last_login = NOW(), last_ip = ? WHERE id = ?")) {
            ps.setString(1, ipAddress);
            ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("updateLastLogin failed for user {}", userId, e);
        }
    }

    private UserRow mapFirst(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;
            Timestamp lastLogin = rs.getTimestamp("last_login");
            return new UserRow(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("figure"),
                rs.getInt("rank"),
                rs.getLong("credits"),
                rs.getLong("diamonds"),
                rs.getLong("nut_points"),
                rs.getBoolean("email_verified"),
                lastLogin != null ? lastLogin.toInstant() : null,
                rs.getString("last_ip")
            );
        }
    }

    // ─── avatar effects ─────────────────────────────────────────────────────

    /** True if the user owns the effect and it has not expired. */
    public boolean ownsEffect(long userId, int effectId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT 1 FROM habnut_user_effects " +
                 "WHERE user_id = ? AND effect_id = ? " +
                 "AND (is_permanent = 1 OR expires_at IS NULL OR expires_at > NOW())")) {
            ps.setLong(1, userId);
            ps.setInt(2, effectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("ownsEffect failed for user {} effect {}", userId, effectId, e);
            return false;
        }
    }

    /** Records which effect the user is currently wearing; 0 clears it. */
    public void setCurrentEffect(long userId, int effectId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_users SET current_effect = ? WHERE id = ?")) {
            ps.setInt(1, Math.max(0, effectId));
            ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("setCurrentEffect failed for user {}", userId, e);
        }
    }

    /** Effect ids the user owns and may currently select. */
    public List<Integer> listEffects(long userId) {
        List<Integer> effects = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT effect_id FROM habnut_user_effects " +
                 "WHERE user_id = ? " +
                 "AND (is_permanent = 1 OR expires_at IS NULL OR expires_at > NOW()) " +
                 "ORDER BY effect_id")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) effects.add(rs.getInt("effect_id"));
            }
        } catch (SQLException e) {
            log.error("listEffects failed for user {}", userId, e);
        }
        return effects;
    }
}
