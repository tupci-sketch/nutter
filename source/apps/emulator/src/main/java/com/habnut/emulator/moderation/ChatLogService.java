package com.habnut.emulator.moderation;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class ChatLogService {

    private static final Logger log = LoggerFactory.getLogger(ChatLogService.class);
    private static final int MAX_RESULTS = 100;

    public record ChatEntry(long id, long userId, String username, long roomId, String roomName,
                            String message, String type, String timestamp) {}

    private final DatabaseManager db;

    public ChatLogService(DatabaseManager db) {
        this.db = db;
    }

    public void write(long userId, String username, long roomId, String roomName,
                      String message, String type) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_chat_logs (user_id, username, room_id, room_name, message, type)" +
                 " VALUES (?,?,?,?,?,?)")) {
            ps.setLong(1, userId);
            ps.setString(2, username);
            ps.setLong(3, roomId);
            ps.setString(4, roomName);
            ps.setString(5, message);
            ps.setString(6, type);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("Failed to write chat log for user {}", userId, e);
        }
    }

    public List<ChatEntry> searchByUser(long userId, int limit) throws SQLException {
        int cap = Math.min(limit, MAX_RESULTS);
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, user_id, username, room_id, room_name, message, type, timestamp" +
                 " FROM habnut_chat_logs WHERE user_id=? ORDER BY timestamp DESC LIMIT ?")) {
            ps.setLong(1, userId);
            ps.setInt(2, cap);
            return mapResults(ps);
        }
    }

    public List<ChatEntry> searchByRoom(long roomId, int limit) throws SQLException {
        int cap = Math.min(limit, MAX_RESULTS);
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, user_id, username, room_id, room_name, message, type, timestamp" +
                 " FROM habnut_chat_logs WHERE room_id=? ORDER BY timestamp DESC LIMIT ?")) {
            ps.setLong(1, roomId);
            ps.setInt(2, cap);
            return mapResults(ps);
        }
    }

    public List<ChatEntry> searchFullText(String query, int limit) throws SQLException {
        int cap = Math.min(limit, MAX_RESULTS);
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, user_id, username, room_id, room_name, message, type, timestamp" +
                 " FROM habnut_chat_logs WHERE MATCH(message) AGAINST(? IN BOOLEAN MODE)" +
                 " ORDER BY timestamp DESC LIMIT ?")) {
            ps.setString(1, query);
            ps.setInt(2, cap);
            return mapResults(ps);
        }
    }

    private List<ChatEntry> mapResults(PreparedStatement ps) throws SQLException {
        List<ChatEntry> results = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new ChatEntry(
                    rs.getLong("id"), rs.getLong("user_id"), rs.getString("username"),
                    rs.getLong("room_id"), rs.getString("room_name"),
                    rs.getString("message"), rs.getString("type"),
                    rs.getString("timestamp")
                ));
            }
        }
        return results;
    }
}
