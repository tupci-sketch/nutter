package com.habnut.emulator.social;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    public record Group(long id, long ownerId, long homeRoomId, String name,
                        String description, String badge, String type,
                        int memberCount, String createdAt) {}

    public record GroupMember(long userId, String username, String figure,
                              String rank, String joinedAt) {}

    public record ForumThread(long id, long groupId, long authorId, String authorUsername,
                              String title, boolean pinned, boolean locked,
                              int replyCount, String createdAt, String lastReplyAt) {}

    public record ForumPost(long id, long threadId, long authorId, String authorUsername,
                            String body, boolean hidden, String createdAt) {}

    private final DatabaseManager db;

    public GroupService(DatabaseManager db) {
        this.db = db;
    }

    public long createGroup(long ownerId, long homeRoomId, String name, String description,
                            String badge, String type) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ins = conn.prepareStatement(
                 "INSERT INTO habnut_groups (owner_id, home_room_id, name, description, badge, type) " +
                 "VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ins.setLong(1, ownerId); ins.setLong(2, homeRoomId);
            ins.setString(3, name); ins.setString(4, description);
            ins.setString(5, badge); ins.setString(6, type);
            ins.executeUpdate();
            try (ResultSet rs = ins.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("No key for group");
                long groupId = rs.getLong(1);
                // Owner is always a member with admin rank
                addMember(conn, groupId, ownerId, "admin");
                return groupId;
            }
        }
    }

    private void addMember(Connection conn, long groupId, long userId, String rank) throws SQLException {
        try (PreparedStatement ins = conn.prepareStatement(
            "INSERT IGNORE INTO habnut_group_members (group_id, user_id, rank) VALUES (?, ?, ?)")) {
            ins.setLong(1, groupId); ins.setLong(2, userId); ins.setString(3, rank);
            ins.executeUpdate();
        }
    }

    public enum JoinResult { JOINED, ALREADY_MEMBER, NOT_FOUND, INVITE_ONLY }

    public JoinResult join(long userId, long groupId) {
        try (Connection conn = db.getConnection()) {
            Optional<Group> g = findById(groupId);
            if (g.isEmpty()) return JoinResult.NOT_FOUND;
            if ("private".equals(g.get().type())) return JoinResult.INVITE_ONLY;
            // Check membership
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM habnut_group_members WHERE group_id = ? AND user_id = ?")) {
                ps.setLong(1, groupId); ps.setLong(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return JoinResult.ALREADY_MEMBER;
                }
            }
            addMember(conn, groupId, userId, "member");
            updateMemberCount(conn, groupId);
            return JoinResult.JOINED;
        } catch (SQLException e) {
            log.error("join failed: user={} group={}", userId, groupId, e);
            return JoinResult.NOT_FOUND;
        }
    }

    public boolean leave(long userId, long groupId) {
        try (Connection conn = db.getConnection();
             PreparedStatement del = conn.prepareStatement(
                 "DELETE FROM habnut_group_members WHERE group_id = ? AND user_id = ? AND rank != 'admin'")) {
            del.setLong(1, groupId); del.setLong(2, userId);
            boolean removed = del.executeUpdate() > 0;
            if (removed) updateMemberCount(conn, groupId);
            return removed;
        } catch (SQLException e) {
            log.error("leave failed: user={} group={}", userId, groupId, e);
            return false;
        }
    }

    public Optional<Group> findById(long groupId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, owner_id, home_room_id, name, description, badge, type, " +
                 "member_count, created_at FROM habnut_groups WHERE id = ?")) {
            ps.setLong(1, groupId);
            List<Group> result = mapGroups(ps);
            return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
        } catch (SQLException e) {
            log.error("findById group failed: {}", groupId, e);
            return Optional.empty();
        }
    }

    public List<Group> search(String query, int limit, int offset) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, owner_id, home_room_id, name, description, badge, type, " +
                 "member_count, created_at FROM habnut_groups " +
                 "WHERE (? = '' OR name LIKE ?) ORDER BY member_count DESC LIMIT ? OFFSET ?")) {
            String q = query.trim();
            ps.setString(1, q); ps.setString(2, "%" + q + "%");
            ps.setInt(3, limit); ps.setInt(4, offset);
            return mapGroups(ps);
        } catch (SQLException e) {
            log.error("group search failed", e);
            return List.of();
        }
    }

    public List<GroupMember> getMembers(long groupId, int limit, int offset) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT u.id, u.username, u.figure, gm.rank, gm.joined_at " +
                 "FROM habnut_group_members gm JOIN habnut_users u ON u.id = gm.user_id " +
                 "WHERE gm.group_id = ? ORDER BY FIELD(gm.rank,'admin','moderator','member'), u.username " +
                 "LIMIT ? OFFSET ?")) {
            ps.setLong(1, groupId); ps.setInt(2, limit); ps.setInt(3, offset);
            List<GroupMember> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new GroupMember(
                        rs.getLong("id"), rs.getString("username"),
                        rs.getString("figure"), rs.getString("rank"),
                        rs.getTimestamp("joined_at").toString()));
                }
            }
            return result;
        } catch (SQLException e) {
            log.error("getMembers failed: group={}", groupId, e);
            return List.of();
        }
    }

    public String getMemberRank(long groupId, long userId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT rank FROM habnut_group_members WHERE group_id = ? AND user_id = ?")) {
            ps.setLong(1, groupId); ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("rank") : null;
            }
        } catch (SQLException e) {
            log.error("getMemberRank failed", e);
            return null;
        }
    }

    // Forum

    public long createThread(long groupId, long authorId, String title) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ins = conn.prepareStatement(
                 "INSERT INTO habnut_forum_threads (group_id, author_id, title) VALUES (?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ins.setLong(1, groupId); ins.setLong(2, authorId); ins.setString(3, title);
            ins.executeUpdate();
            try (ResultSet rs = ins.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("No key for thread");
                return rs.getLong(1);
            }
        }
    }

    public long createPost(long threadId, long authorId, String body) throws SQLException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            long postId;
            try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO habnut_forum_posts (thread_id, author_id, body) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
                ins.setLong(1, threadId); ins.setLong(2, authorId); ins.setString(3, body);
                ins.executeUpdate();
                try (ResultSet rs = ins.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("No key for post");
                    postId = rs.getLong(1);
                }
            }
            try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE habnut_forum_threads SET reply_count = reply_count + 1, " +
                "last_reply_at = NOW() WHERE id = ?")) {
                upd.setLong(1, threadId); upd.executeUpdate();
            }
            conn.commit();
            return postId;
        }
    }

    public List<ForumThread> getThreads(long groupId, int limit, int offset) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT t.id, t.group_id, t.author_id, u.username as author_username, t.title, " +
                 "t.pinned, t.locked, t.reply_count, t.created_at, t.last_reply_at " +
                 "FROM habnut_forum_threads t JOIN habnut_users u ON u.id = t.author_id " +
                 "WHERE t.group_id = ? AND t.hidden = 0 " +
                 "ORDER BY t.pinned DESC, t.last_reply_at DESC LIMIT ? OFFSET ?")) {
            ps.setLong(1, groupId); ps.setInt(2, limit); ps.setInt(3, offset);
            return mapThreads(ps);
        } catch (SQLException e) {
            log.error("getThreads failed: group={}", groupId, e);
            return List.of();
        }
    }

    public List<ForumPost> getPosts(long threadId, int limit, int offset) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT p.id, p.thread_id, p.author_id, u.username as author_username, " +
                 "p.body, p.hidden, p.created_at " +
                 "FROM habnut_forum_posts p JOIN habnut_users u ON u.id = p.author_id " +
                 "WHERE p.thread_id = ? ORDER BY p.created_at ASC LIMIT ? OFFSET ?")) {
            ps.setLong(1, threadId); ps.setInt(2, limit); ps.setInt(3, offset);
            return mapPosts(ps);
        } catch (SQLException e) {
            log.error("getPosts failed: thread={}", threadId, e);
            return List.of();
        }
    }

    private void updateMemberCount(Connection conn, long groupId) throws SQLException {
        try (PreparedStatement upd = conn.prepareStatement(
            "UPDATE habnut_groups SET member_count = " +
            "(SELECT COUNT(*) FROM habnut_group_members WHERE group_id = ?) WHERE id = ?")) {
            upd.setLong(1, groupId); upd.setLong(2, groupId);
            upd.executeUpdate();
        }
    }

    private List<Group> mapGroups(PreparedStatement ps) throws SQLException {
        List<Group> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new Group(
                    rs.getLong("id"), rs.getLong("owner_id"), rs.getLong("home_room_id"),
                    rs.getString("name"), rs.getString("description"),
                    rs.getString("badge"), rs.getString("type"),
                    rs.getInt("member_count"), rs.getTimestamp("created_at").toString()));
            }
        }
        return result;
    }

    private List<ForumThread> mapThreads(PreparedStatement ps) throws SQLException {
        List<ForumThread> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp lastReply = rs.getTimestamp("last_reply_at");
                result.add(new ForumThread(
                    rs.getLong("id"), rs.getLong("group_id"),
                    rs.getLong("author_id"), rs.getString("author_username"),
                    rs.getString("title"), rs.getBoolean("pinned"), rs.getBoolean("locked"),
                    rs.getInt("reply_count"), rs.getTimestamp("created_at").toString(),
                    lastReply != null ? lastReply.toString() : null));
            }
        }
        return result;
    }

    private List<ForumPost> mapPosts(PreparedStatement ps) throws SQLException {
        List<ForumPost> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new ForumPost(
                    rs.getLong("id"), rs.getLong("thread_id"),
                    rs.getLong("author_id"), rs.getString("author_username"),
                    rs.getString("body"), rs.getBoolean("hidden"),
                    rs.getTimestamp("created_at").toString()));
            }
        }
        return result;
    }
}
