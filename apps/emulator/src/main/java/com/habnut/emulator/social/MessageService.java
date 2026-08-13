package com.habnut.emulator.social;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final int MAX_LENGTH = 1000;

    public record Message(long id, long fromUserId, String fromUsername,
                          long toUserId, String body, boolean read, String sentAt) {}

    private final DatabaseManager db;

    public MessageService(DatabaseManager db) {
        this.db = db;
    }

    public List<Message> getInbox(long userId, int limit, int offset) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT m.id, m.from_user_id, u.username as from_username, m.to_user_id, " +
                 "m.body, m.read_at IS NOT NULL as is_read, m.created_at " +
                 "FROM habnut_messages m JOIN habnut_users u ON u.id = m.from_user_id " +
                 "WHERE m.to_user_id = ? AND m.deleted_by_recipient = 0 " +
                 "ORDER BY m.created_at DESC LIMIT ? OFFSET ?")) {
            ps.setLong(1, userId); ps.setInt(2, limit); ps.setInt(3, offset);
            return mapMessages(ps);
        } catch (SQLException e) {
            log.error("getInbox failed for user {}", userId, e);
            return List.of();
        }
    }

    public List<Message> getSent(long userId, int limit, int offset) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT m.id, m.from_user_id, u.username as from_username, m.to_user_id, " +
                 "m.body, m.read_at IS NOT NULL as is_read, m.created_at " +
                 "FROM habnut_messages m JOIN habnut_users u ON u.id = m.from_user_id " +
                 "WHERE m.from_user_id = ? AND m.deleted_by_sender = 0 " +
                 "ORDER BY m.created_at DESC LIMIT ? OFFSET ?")) {
            ps.setLong(1, userId); ps.setInt(2, limit); ps.setInt(3, offset);
            return mapMessages(ps);
        } catch (SQLException e) {
            log.error("getSent failed for user {}", userId, e);
            return List.of();
        }
    }

    public enum SendResult { SENT, NOT_FRIENDS, BLOCKED, TOO_LONG }

    public SendResult send(long fromUserId, long toUserId, String body,
                           FriendService friendService) {
        if (body == null || body.isBlank()) return SendResult.TOO_LONG;
        body = body.trim();
        if (body.length() > MAX_LENGTH) return SendResult.TOO_LONG;
        if (!friendService.areFriends(fromUserId, toUserId)) return SendResult.NOT_FRIENDS;
        try (Connection conn = db.getConnection();
             PreparedStatement ins = conn.prepareStatement(
                 "INSERT INTO habnut_messages (from_user_id, to_user_id, body) VALUES (?, ?, ?)")) {
            ins.setLong(1, fromUserId); ins.setLong(2, toUserId); ins.setString(3, body);
            ins.executeUpdate();
            return SendResult.SENT;
        } catch (SQLException e) {
            log.error("send message failed from {} to {}", fromUserId, toUserId, e);
            return SendResult.BLOCKED;
        }
    }

    public boolean markRead(long messageId, long userId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_messages SET read_at = NOW() " +
                 "WHERE id = ? AND to_user_id = ? AND read_at IS NULL")) {
            ps.setLong(1, messageId); ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("markRead failed: messageId={}", messageId, e);
            return false;
        }
    }

    public boolean deleteByRecipient(long messageId, long userId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_messages SET deleted_by_recipient = 1 WHERE id = ? AND to_user_id = ?")) {
            ps.setLong(1, messageId); ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("deleteByRecipient failed: messageId={}", messageId, e);
            return false;
        }
    }

    private List<Message> mapMessages(PreparedStatement ps) throws SQLException {
        List<Message> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new Message(
                    rs.getLong("id"), rs.getLong("from_user_id"),
                    rs.getString("from_username"), rs.getLong("to_user_id"),
                    rs.getString("body"), rs.getBoolean("is_read"),
                    rs.getTimestamp("created_at").toString()));
            }
        }
        return result;
    }
}
