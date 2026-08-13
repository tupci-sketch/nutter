package com.habnut.emulator.social;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FriendService {

    private static final Logger log = LoggerFactory.getLogger(FriendService.class);

    public record Friend(long userId, String username, String figure, String motto,
                         boolean online, String lastSeen) {}

    public record FriendRequest(long requestId, long fromUserId, String fromUsername,
                                String fromFigure, String sentAt) {}

    private final DatabaseManager db;

    public FriendService(DatabaseManager db) {
        this.db = db;
    }

    public List<Friend> getFriends(long userId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT u.id, u.username, u.figure, u.motto, u.online, u.last_seen " +
                 "FROM habnut_friendships f " +
                 "JOIN habnut_users u ON u.id = CASE WHEN f.user_a = ? THEN f.user_b ELSE f.user_a END " +
                 "WHERE (f.user_a = ? OR f.user_b = ?) AND f.accepted = 1 " +
                 "ORDER BY u.username ASC")) {
            ps.setLong(1, userId); ps.setLong(2, userId); ps.setLong(3, userId);
            return mapFriends(ps);
        } catch (SQLException e) {
            log.error("getFriends failed for user {}", userId, e);
            return List.of();
        }
    }

    public List<FriendRequest> getPendingRequests(long userId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT f.id, u.id as from_id, u.username, u.figure, f.created_at " +
                 "FROM habnut_friendships f " +
                 "JOIN habnut_users u ON u.id = f.user_a " +
                 "WHERE f.user_b = ? AND f.accepted = 0 ORDER BY f.created_at DESC")) {
            ps.setLong(1, userId);
            List<FriendRequest> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new FriendRequest(
                        rs.getLong("id"), rs.getLong("from_id"),
                        rs.getString("username"), rs.getString("figure"),
                        rs.getTimestamp("created_at").toString()));
                }
            }
            return result;
        } catch (SQLException e) {
            log.error("getPendingRequests failed for user {}", userId, e);
            return List.of();
        }
    }

    public enum SendRequestResult { SENT, ALREADY_FRIENDS, ALREADY_PENDING, BLOCKED, SELF }

    public SendRequestResult sendRequest(long fromUserId, long toUserId) {
        if (fromUserId == toUserId) return SendRequestResult.SELF;
        try (Connection conn = db.getConnection()) {
            // Check existing relationship
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT accepted FROM habnut_friendships " +
                "WHERE (user_a = ? AND user_b = ?) OR (user_a = ? AND user_b = ?)")) {
                ps.setLong(1, fromUserId); ps.setLong(2, toUserId);
                ps.setLong(3, toUserId); ps.setLong(4, fromUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBoolean("accepted")
                            ? SendRequestResult.ALREADY_FRIENDS
                            : SendRequestResult.ALREADY_PENDING;
                    }
                }
            }
            // Check blocks
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM habnut_blocks " +
                "WHERE (blocker_id = ? AND blocked_id = ?) OR (blocker_id = ? AND blocked_id = ?)")) {
                ps.setLong(1, fromUserId); ps.setLong(2, toUserId);
                ps.setLong(3, toUserId); ps.setLong(4, fromUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return SendRequestResult.BLOCKED;
                }
            }
            try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO habnut_friendships (user_a, user_b, accepted) VALUES (?, ?, 0)")) {
                ins.setLong(1, fromUserId); ins.setLong(2, toUserId);
                ins.executeUpdate();
            }
            return SendRequestResult.SENT;
        } catch (SQLException e) {
            log.error("sendRequest failed from {} to {}", fromUserId, toUserId, e);
            return SendRequestResult.BLOCKED;
        }
    }

    public boolean acceptRequest(long requestId, long acceptingUserId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_friendships SET accepted = 1, accepted_at = NOW() " +
                 "WHERE id = ? AND user_b = ? AND accepted = 0")) {
            ps.setLong(1, requestId); ps.setLong(2, acceptingUserId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("acceptRequest failed: requestId={}", requestId, e);
            return false;
        }
    }

    public boolean declineRequest(long requestId, long decliningUserId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM habnut_friendships WHERE id = ? AND user_b = ? AND accepted = 0")) {
            ps.setLong(1, requestId); ps.setLong(2, decliningUserId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("declineRequest failed: requestId={}", requestId, e);
            return false;
        }
    }

    public boolean removeFriend(long userId, long friendId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM habnut_friendships " +
                 "WHERE ((user_a = ? AND user_b = ?) OR (user_a = ? AND user_b = ?)) AND accepted = 1")) {
            ps.setLong(1, userId); ps.setLong(2, friendId);
            ps.setLong(3, friendId); ps.setLong(4, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("removeFriend failed: {} {}", userId, friendId, e);
            return false;
        }
    }

    public boolean block(long blockerId, long blockedId) {
        if (blockerId == blockedId) return false;
        try (Connection conn = db.getConnection()) {
            // Remove friendship if exists
            try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM habnut_friendships " +
                "WHERE (user_a = ? AND user_b = ?) OR (user_a = ? AND user_b = ?)")) {
                del.setLong(1, blockerId); del.setLong(2, blockedId);
                del.setLong(3, blockedId); del.setLong(4, blockerId);
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(
                "INSERT IGNORE INTO habnut_blocks (blocker_id, blocked_id) VALUES (?, ?)")) {
                ins.setLong(1, blockerId); ins.setLong(2, blockedId);
                ins.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            log.error("block failed: {} {}", blockerId, blockedId, e);
            return false;
        }
    }

    public boolean areFriends(long userA, long userB) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT 1 FROM habnut_friendships " +
                 "WHERE ((user_a = ? AND user_b = ?) OR (user_a = ? AND user_b = ?)) AND accepted = 1")) {
            ps.setLong(1, userA); ps.setLong(2, userB);
            ps.setLong(3, userB); ps.setLong(4, userA);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            log.error("areFriends check failed", e);
            return false;
        }
    }

    public Optional<Friend> getFriend(long userId, long friendId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT u.id, u.username, u.figure, u.motto, u.online, u.last_seen " +
                 "FROM habnut_friendships f " +
                 "JOIN habnut_users u ON u.id = ? " +
                 "WHERE ((f.user_a = ? AND f.user_b = ?) OR (f.user_a = ? AND f.user_b = ?)) AND f.accepted = 1")) {
            ps.setLong(1, friendId);
            ps.setLong(2, userId); ps.setLong(3, friendId);
            ps.setLong(4, friendId); ps.setLong(5, userId);
            List<Friend> l = mapFriends(ps);
            return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
        } catch (SQLException e) {
            log.error("getFriend failed", e);
            return Optional.empty();
        }
    }

    private List<Friend> mapFriends(PreparedStatement ps) throws SQLException {
        List<Friend> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp lastSeen = rs.getTimestamp("last_seen");
                result.add(new Friend(
                    rs.getLong("id"), rs.getString("username"),
                    rs.getString("figure"), rs.getString("motto"),
                    rs.getBoolean("online"),
                    lastSeen != null ? lastSeen.toString() : null));
            }
        }
        return result;
    }
}
