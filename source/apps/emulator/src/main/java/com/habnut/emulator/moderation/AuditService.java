package com.habnut.emulator.moderation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Map;

public final class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final DatabaseManager db;

    public AuditService(DatabaseManager db) {
        this.db = db;
    }

    public void log(long performedById, String performedByName, String performedByIp,
                    String action, String targetType, String targetId, String targetName,
                    Object beforeState, Object afterState, Map<String, Object> metadata,
                    String sessionId, Long roomId, String worldId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_audit_logs (performed_by_id, performed_by_name, performed_by_ip," +
                 " action, target_type, target_id, target_name, before_state, after_state, metadata," +
                 " session_id, room_id, world_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setLong(1, performedById);
            ps.setString(2, performedByName);
            ps.setString(3, performedByIp != null ? performedByIp : "unknown");
            ps.setString(4, action);
            ps.setString(5, targetType);
            ps.setString(6, targetId);
            ps.setString(7, targetName);
            ps.setString(8, beforeState != null ? JSON.writeValueAsString(beforeState) : null);
            ps.setString(9, afterState  != null ? JSON.writeValueAsString(afterState)  : null);
            ps.setString(10, metadata != null ? JSON.writeValueAsString(metadata) : "{}");
            ps.setString(11, sessionId);
            if (roomId != null) ps.setLong(12, roomId); else ps.setNull(12, Types.BIGINT);
            ps.setString(13, worldId != null ? worldId : "system");
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Audit log write failed: action={} target={}/{}", action, targetType, targetId, e);
        }
    }
}
