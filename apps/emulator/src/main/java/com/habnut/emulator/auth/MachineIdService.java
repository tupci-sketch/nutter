package com.habnut.emulator.auth;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class MachineIdService {

    private static final Logger log = LoggerFactory.getLogger(MachineIdService.class);

    private final DatabaseManager db;

    public MachineIdService(DatabaseManager db) {
        this.db = db;
    }

    public void record(long userId, String machineId, String ipAddress) {
        if (machineId == null || machineId.isBlank()) return;
        try (Connection conn = db.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO habnut_machine_ids (user_id, machine_id, ip_address) " +
                "VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE ip_address = VALUES(ip_address), last_seen_at = NOW()")) {
                ps.setLong(1, userId);
                ps.setString(2, machineId);
                ps.setString(3, ipAddress);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            log.warn("Failed to record machine ID for user {}", userId, e);
        }
    }

    public boolean isBanned(String machineId) {
        if (machineId == null || machineId.isBlank()) return false;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM habnut_machine_ids mid " +
                 "JOIN habnut_bans b ON b.user_id = mid.user_id " +
                 "WHERE mid.machine_id = ? AND b.ban_type = 'machine' " +
                 "AND (b.expires_at IS NULL OR b.expires_at > NOW()) AND b.revoked_at IS NULL")) {
            ps.setString(1, machineId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            log.warn("Machine ID ban check failed for {}", machineId, e);
            return false;
        }
    }
}
