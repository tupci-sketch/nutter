package com.habnut.emulator.moderation;

import com.habnut.emulator.auth.UserRepository;
import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ModerationService {

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);

    public static final int RANK_MOD   = 4;
    public static final int RANK_ADMIN = 7;

    public record BanRecord(long id, long userId, String username, long bannedById,
                            String reason, String banType, String expiresAt, boolean active) {}
    public record MuteRecord(long id, long userId, long mutedById, String reason,
                             Long roomId, String expiresAt) {}
    public record Report(long id, long reporterId, String reporterName, long targetId,
                         String targetName, String category, String description,
                         String status, Long claimedById, String createdAt) {}
    public record Appeal(long id, long userId, long banId, String message, String status,
                         String submittedAt) {}
    public record UserSummary(long id, String username, int rank, String lastIp,
                              int openReports, int totalBans, boolean currentlyMuted) {}

    private final DatabaseManager db;
    private final UserRepository userRepo;

    public ModerationService(DatabaseManager db, UserRepository userRepo) {
        this.db       = db;
        this.userRepo = userRepo;
    }

    public boolean isActiveBan(long userId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id FROM habnut_bans WHERE user_id=? AND lifted_at IS NULL" +
                 " AND (expires_at IS NULL OR expires_at > NOW()) LIMIT 1")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public boolean isActiveMute(long userId, Long roomId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id FROM habnut_mutes WHERE user_id=? AND lifted_at IS NULL" +
                 " AND expires_at > NOW() AND (room_id IS NULL OR room_id=?) LIMIT 1")) {
            ps.setLong(1, userId);
            if (roomId != null) ps.setLong(2, roomId); else ps.setNull(2, Types.BIGINT);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public long mute(long staffId, long targetId, String reason, Long roomId,
                     int durationMinutes) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_mutes (user_id, muted_by_id, reason, room_id, expires_at)" +
                 " VALUES (?,?,?,?,DATE_ADD(NOW(), INTERVAL ? MINUTE))",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, targetId);
            ps.setLong(2, staffId);
            ps.setString(3, reason);
            if (roomId != null) ps.setLong(4, roomId); else ps.setNull(4, Types.BIGINT);
            ps.setInt(5, durationMinutes);
            ps.executeUpdate();
            try (ResultSet gk = ps.getGeneratedKeys()) { gk.next(); return gk.getLong(1); }
        }
    }

    public boolean unmute(long staffId, long targetId, Long roomId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_mutes SET lifted_at=NOW(), lifted_by_id=?" +
                 " WHERE user_id=? AND lifted_at IS NULL AND expires_at>NOW()" +
                 " AND (room_id IS NULL OR room_id=?) LIMIT 1")) {
            ps.setLong(1, staffId);
            ps.setLong(2, targetId);
            if (roomId != null) ps.setLong(3, roomId); else ps.setNull(3, Types.BIGINT);
            return ps.executeUpdate() > 0;
        }
    }

    public long ban(long staffId, long targetId, String reason, String banType,
                    String ipAddress, String machineIdHash,
                    Integer durationHours) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_bans (user_id, banned_by_id, reason, ban_type," +
                 " ip_address, machine_id_hash, expires_at) VALUES (?,?,?,?,?,?," +
                 " IF(?,DATE_ADD(NOW(),INTERVAL ? HOUR),NULL))",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, targetId);
            ps.setLong(2, staffId);
            ps.setString(3, reason);
            ps.setString(4, banType != null ? banType : "standard");
            ps.setString(5, ipAddress);
            ps.setString(6, machineIdHash);
            ps.setBoolean(7, durationHours != null);
            ps.setInt(8, durationHours != null ? durationHours : 0);
            ps.executeUpdate();
            recordModAction(conn, staffId, targetId, "ban", reason, null, null);
            try (ResultSet gk = ps.getGeneratedKeys()) { gk.next(); return gk.getLong(1); }
        }
    }

    public boolean unban(long staffId, long banId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_bans SET lifted_at=NOW(), lifted_by_id=?" +
                 " WHERE id=? AND lifted_at IS NULL")) {
            ps.setLong(1, staffId);
            ps.setLong(2, banId);
            if (ps.executeUpdate() > 0) {
                long targetId = getBanTargetId(conn, banId);
                recordModAction(conn, staffId, targetId, "unban", "appeal/manual", null, null);
                return true;
            }
            return false;
        }
    }

    public void warn(long staffId, long targetId, String reason) throws SQLException {
        try (Connection conn = db.getConnection()) {
            recordModAction(conn, staffId, targetId, "warn", reason, null, null);
        }
    }

    public long createReport(long reporterId, long targetId, String category,
                             String description, Long roomId, String roomName,
                             List<String> chatContext) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_reports (reporter_id, target_id, category, description," +
                 " chat_context, room_id, room_name) VALUES (?,?,?,?,?,?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, reporterId);
            ps.setLong(2, targetId);
            ps.setString(3, category);
            ps.setString(4, description);
            ps.setString(5, chatContext != null ? com.fasterxml.jackson.databind.util.RawValue.class.getName() : "[]");
            if (roomId != null) ps.setLong(6, roomId); else ps.setNull(6, Types.BIGINT);
            ps.setString(7, roomName);
            ps.executeUpdate();
            try (ResultSet gk = ps.getGeneratedKeys()) { gk.next(); return gk.getLong(1); }
        }
    }

    public long createReport(long reporterId, long targetId, String category,
                             String description, Long roomId, String roomName) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_reports (reporter_id, target_id, category, description," +
                 " chat_context, room_id, room_name) VALUES (?,?,?,?,JSON_ARRAY(),?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, reporterId);
            ps.setLong(2, targetId);
            ps.setString(3, category);
            ps.setString(4, description);
            if (roomId != null) ps.setLong(5, roomId); else ps.setNull(5, Types.BIGINT);
            ps.setString(6, roomName);
            ps.executeUpdate();
            try (ResultSet gk = ps.getGeneratedKeys()) { gk.next(); return gk.getLong(1); }
        }
    }

    public List<Report> listOpenReports(int limit) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT r.id, r.reporter_id, u1.username reporter_name, r.target_id," +
                 " u2.username target_name, r.category, r.description, r.status," +
                 " r.claimed_by_id, r.created_at" +
                 " FROM habnut_reports r" +
                 " JOIN habnut_users u1 ON u1.id=r.reporter_id" +
                 " JOIN habnut_users u2 ON u2.id=r.target_id" +
                 " WHERE r.status IN ('open','claimed') ORDER BY r.created_at ASC LIMIT ?")) {
            ps.setInt(1, Math.min(limit, 50));
            return mapReports(ps);
        }
    }

    public boolean claimReport(long reportId, long staffId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_reports SET status='claimed', claimed_by_id=?" +
                 " WHERE id=? AND status='open'")) {
            ps.setLong(1, staffId);
            ps.setLong(2, reportId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean resolveReport(long reportId, long staffId, String resolution,
                                  String outcome) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_reports SET status=?, claimed_by_id=?, resolution=?," +
                 " resolved_at=NOW() WHERE id=? AND status IN ('open','claimed')")) {
            ps.setString(1, "dismissed".equals(outcome) ? "dismissed" : "resolved");
            ps.setLong(2, staffId);
            ps.setString(3, resolution);
            ps.setLong(4, reportId);
            return ps.executeUpdate() > 0;
        }
    }

    public long submitAppeal(long userId, long banId, String message) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_appeals (user_id, ban_id, message) VALUES (?,?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setLong(2, banId);
            ps.setString(3, message);
            ps.executeUpdate();
            try (ResultSet gk = ps.getGeneratedKeys()) { gk.next(); return gk.getLong(1); }
        }
    }

    public boolean reviewAppeal(long appealId, long staffId, String decision,
                                 String reviewNote) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_appeals SET status=?, reviewed_by_id=?, review_note=?," +
                 " reviewed_at=NOW() WHERE id=? AND status='pending'")) {
            ps.setString(1, decision);
            ps.setLong(2, staffId);
            ps.setString(3, reviewNote);
            ps.setLong(4, appealId);
            return ps.executeUpdate() > 0;
        }
    }

    public Optional<UserSummary> getUserSummary(long targetId) throws SQLException {
        UserRepository.UserRow user = userRepo.findById(targetId);
        if (user == null) return Optional.empty();

        try (Connection conn = db.getConnection()) {
            int openReports = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM habnut_reports WHERE target_id=? AND status='open'")) {
                ps.setLong(1, targetId);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) openReports = rs.getInt(1); }
            }
            int totalBans = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM habnut_bans WHERE user_id=?")) {
                ps.setLong(1, targetId);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) totalBans = rs.getInt(1); }
            }
            boolean muted = isActiveMute(targetId, null);
            return Optional.of(new UserSummary(user.id(), user.username(), user.rank(),
                user.lastIp(), openReports, totalBans, muted));
        }
    }

    public List<BanRecord> getBans(long userId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT b.id, b.user_id, u.username, b.banned_by_id, b.reason, b.ban_type," +
                 " b.expires_at, (b.lifted_at IS NULL AND (b.expires_at IS NULL OR b.expires_at>NOW())) active" +
                 " FROM habnut_bans b JOIN habnut_users u ON u.id=b.user_id" +
                 " WHERE b.user_id=? ORDER BY b.created_at DESC LIMIT 20")) {
            ps.setLong(1, userId);
            List<BanRecord> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new BanRecord(rs.getLong("id"), rs.getLong("user_id"),
                        rs.getString("username"), rs.getLong("banned_by_id"),
                        rs.getString("reason"), rs.getString("ban_type"),
                        rs.getString("expires_at"), rs.getBoolean("active")));
                }
            }
            return list;
        }
    }

    private void recordModAction(Connection conn, long performedById, long targetId,
                                  String actionType, String reason,
                                  Long durationMs, Long roomId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_moderation_actions" +
                 " (performed_by_id, target_id, action_type, reason, duration_ms, room_id)" +
                 " VALUES (?,?,?,?,?,?)")) {
            ps.setLong(1, performedById);
            ps.setLong(2, targetId);
            ps.setString(3, actionType);
            ps.setString(4, reason);
            if (durationMs != null) ps.setLong(5, durationMs); else ps.setNull(5, Types.BIGINT);
            if (roomId != null) ps.setLong(6, roomId); else ps.setNull(6, Types.BIGINT);
            ps.executeUpdate();
        }
    }

    private long getBanTargetId(Connection conn, long banId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT user_id FROM habnut_bans WHERE id=?")) {
            ps.setLong(1, banId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("user_id") : -1;
            }
        }
    }

    private List<Report> mapReports(PreparedStatement ps) throws SQLException {
        List<Report> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Report(
                    rs.getLong("id"), rs.getLong("reporter_id"), rs.getString("reporter_name"),
                    rs.getLong("target_id"), rs.getString("target_name"),
                    rs.getString("category"), rs.getString("description"),
                    rs.getString("status"),
                    rs.getObject("claimed_by_id") != null ? rs.getLong("claimed_by_id") : null,
                    rs.getString("created_at")
                ));
            }
        }
        return list;
    }
}
