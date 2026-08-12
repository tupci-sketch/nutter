package com.habnut.emulator.auth;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                 "SELECT id, username, email, password_hash, figure_string, rank_id, " +
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
                 "SELECT id, username, email, password_hash, figure_string, rank_id, " +
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
                rs.getString("figure_string"),
                rs.getInt("rank_id"),
                rs.getLong("credits"),
                rs.getLong("diamonds"),
                rs.getLong("nut_points"),
                rs.getBoolean("email_verified"),
                lastLogin != null ? lastLogin.toInstant() : null,
                rs.getString("last_ip")
            );
        }
    }
}
