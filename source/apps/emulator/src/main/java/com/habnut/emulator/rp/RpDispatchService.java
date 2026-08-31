package com.habnut.emulator.rp;

import com.habnut.emulator.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RpDispatchService {

    public record DispatchCall(long id, long callerCharId, String callType, String location,
                               String description, int priority, String status,
                               Long assignedCharId, String createdAt) {}

    private final DatabaseManager db;

    public RpDispatchService(DatabaseManager db) {
        this.db = db;
    }

    public long create(long callerCharId, String callType, String location,
                        String description, int priority) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_rp_dispatch_calls" +
                 " (caller_character_id, call_type, location, description, priority)" +
                 " VALUES (?,?,?,?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, callerCharId);
            ps.setString(2, callType);
            ps.setString(3, location);
            ps.setString(4, description);
            ps.setInt(5, Math.max(1, Math.min(5, priority)));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public List<DispatchCall> listPending(String callType) throws SQLException {
        String sql = "SELECT id, caller_character_id, call_type, location, description," +
                     " priority, status, assigned_character_id, created_at" +
                     " FROM habnut_rp_dispatch_calls" +
                     " WHERE status='pending'" +
                     (callType != null ? " AND call_type=?" : "") +
                     " ORDER BY priority DESC, created_at ASC LIMIT 50";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (callType != null) ps.setString(1, callType);
            return mapCalls(ps);
        }
    }

    public boolean accept(long callId, long charId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_rp_dispatch_calls SET status='active'," +
                 " assigned_character_id=?, accepted_at=NOW()" +
                 " WHERE id=? AND status='pending'")) {
            ps.setLong(1, charId);
            ps.setLong(2, callId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean resolve(long callId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_rp_dispatch_calls SET status='resolved', resolved_at=NOW()" +
                 " WHERE id=? AND status='active'")) {
            ps.setLong(1, callId);
            return ps.executeUpdate() > 0;
        }
    }

    public Optional<DispatchCall> findById(long callId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, caller_character_id, call_type, location, description," +
                 " priority, status, assigned_character_id, created_at" +
                 " FROM habnut_rp_dispatch_calls WHERE id=?")) {
            ps.setLong(1, callId);
            List<DispatchCall> list = mapCalls(ps);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }
    }

    private List<DispatchCall> mapCalls(PreparedStatement ps) throws SQLException {
        List<DispatchCall> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Object aId = rs.getObject("assigned_character_id");
                list.add(new DispatchCall(rs.getLong("id"), rs.getLong("caller_character_id"),
                    rs.getString("call_type"), rs.getString("location"),
                    rs.getString("description"), rs.getInt("priority"),
                    rs.getString("status"),
                    aId != null ? ((Number) aId).longValue() : null,
                    rs.getString("created_at")));
            }
        }
        return list;
    }
}
