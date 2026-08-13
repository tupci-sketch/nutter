package com.habnut.emulator.events;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    public record Event(long id, String name, String description, String type,
                        String startsAt, String endsAt, Long roomId,
                        long hostUserId, Integer maxParticipants, String status) {}
    public record ParticipantCount(long eventId, int count) {}

    private final DatabaseManager db;

    public EventService(DatabaseManager db) {
        this.db = db;
    }

    public long create(long hostUserId, String name, String description, String type,
                       String startsAt, String endsAt, Long roomId,
                       Integer maxParticipants) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_events (name, description, type, starts_at, ends_at," +
                 " room_id, host_user_id, max_participants) VALUES (?,?,?,?,?,?,?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, description != null ? description : "");
            ps.setString(3, type != null ? type : "social");
            ps.setString(4, startsAt);
            ps.setString(5, endsAt);
            if (roomId != null) ps.setLong(6, roomId); else ps.setNull(6, Types.BIGINT);
            ps.setLong(7, hostUserId);
            if (maxParticipants != null) ps.setInt(8, maxParticipants); else ps.setNull(8, Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet gk = ps.getGeneratedKeys()) { gk.next(); return gk.getLong(1); }
        }
    }

    public List<Event> listActive() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, name, description, type, starts_at, ends_at, room_id," +
                 " host_user_id, max_participants, status FROM habnut_events" +
                 " WHERE status IN ('scheduled','active') ORDER BY starts_at ASC LIMIT 50")) {
            return mapEvents(ps);
        }
    }

    public Optional<Event> findById(long eventId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, name, description, type, starts_at, ends_at, room_id," +
                 " host_user_id, max_participants, status FROM habnut_events WHERE id=?")) {
            ps.setLong(1, eventId);
            List<Event> list = mapEvents(ps);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }
    }

    public boolean join(long eventId, long userId) throws SQLException {
        try (Connection conn = db.getConnection()) {
            Optional<Event> opt = findById(eventId);
            if (opt.isEmpty() || "ended".equals(opt.get().status()) ||
                "cancelled".equals(opt.get().status())) return false;

            if (opt.get().maxParticipants() != null) {
                int count = participantCount(conn, eventId);
                if (count >= opt.get().maxParticipants()) return false;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                     "INSERT IGNORE INTO habnut_event_participants (event_id, user_id)" +
                     " VALUES (?,?)")) {
                ps.setLong(1, eventId); ps.setLong(2, userId);
                return ps.executeUpdate() > 0;
            }
        }
    }

    public boolean leave(long eventId, long userId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM habnut_event_participants WHERE event_id=? AND user_id=?")) {
            ps.setLong(1, eventId); ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean setStatus(long eventId, String status) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_events SET status=? WHERE id=?")) {
            ps.setString(1, status); ps.setLong(2, eventId);
            return ps.executeUpdate() > 0;
        }
    }

    public int participantCount(long eventId) throws SQLException {
        try (Connection conn = db.getConnection()) {
            return participantCount(conn, eventId);
        }
    }

    private int participantCount(Connection conn, long eventId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM habnut_event_participants WHERE event_id=?")) {
            ps.setLong(1, eventId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private List<Event> mapEvents(PreparedStatement ps) throws SQLException {
        List<Event> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Object roomIdVal = rs.getObject("room_id");
                Object maxPart   = rs.getObject("max_participants");
                list.add(new Event(
                    rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                    rs.getString("type"), rs.getString("starts_at"), rs.getString("ends_at"),
                    roomIdVal != null ? ((Number) roomIdVal).longValue() : null,
                    rs.getLong("host_user_id"),
                    maxPart != null ? ((Number) maxPart).intValue() : null,
                    rs.getString("status")
                ));
            }
        }
        return list;
    }
}
