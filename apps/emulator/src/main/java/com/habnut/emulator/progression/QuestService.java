package com.habnut.emulator.progression;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class QuestService {

    private static final Logger log = LoggerFactory.getLogger(QuestService.class);

    public record Quest(long id, String code, String name, String description,
                        String category, String type, int rewardCredits, int rewardDiamonds,
                        int rewardNutPoints, String badgeCode, int progress,
                        int requiredProgress, boolean completed, String expiresAt) {}

    private final DatabaseManager db;

    public QuestService(DatabaseManager db) {
        this.db = db;
    }

    public List<Quest> getActive(long userId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT qd.id, qd.code, qd.name, qd.description, qd.category, qd.type, " +
                 "qd.reward_credits, qd.reward_diamonds, qd.reward_nut_points, qd.badge_code, " +
                 "qd.required_progress, " +
                 "COALESCE(uq.progress, 0) as progress, " +
                 "uq.completed_at IS NOT NULL as completed, qd.expires_at " +
                 "FROM habnut_quests_def qd " +
                 "LEFT JOIN habnut_user_quests uq ON uq.quest_id = qd.id AND uq.user_id = ? " +
                 "WHERE (qd.expires_at IS NULL OR qd.expires_at > NOW()) " +
                 "AND (uq.completed_at IS NULL OR uq.completed_at IS NOT NULL) " +
                 "AND (uq.abandoned_at IS NULL) " +
                 "ORDER BY qd.type, qd.id")) {
            ps.setLong(1, userId);
            return mapQuests(ps);
        } catch (SQLException e) {
            log.error("getActive quests failed: user={}", userId, e);
            return List.of();
        }
    }

    public boolean accept(long userId, long questId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ins = conn.prepareStatement(
                 "INSERT IGNORE INTO habnut_user_quests (user_id, quest_id) VALUES (?, ?)")) {
            ins.setLong(1, userId); ins.setLong(2, questId);
            return ins.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("accept quest failed: user={} quest={}", userId, questId, e);
            return false;
        }
    }

    public boolean abandon(long userId, long questId) {
        try (Connection conn = db.getConnection();
             PreparedStatement upd = conn.prepareStatement(
                 "UPDATE habnut_user_quests SET abandoned_at = NOW() " +
                 "WHERE user_id = ? AND quest_id = ? AND completed_at IS NULL")) {
            upd.setLong(1, userId); upd.setLong(2, questId);
            return upd.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("abandon quest failed: user={} quest={}", userId, questId, e);
            return false;
        }
    }

    public record ProgressResult(boolean justCompleted, int newProgress,
                                 int rewardCredits, int rewardDiamonds, int rewardNutPoints,
                                 String badgeCode) {}

    public java.util.Optional<ProgressResult> incrementProgress(long userId, long questId, int delta) {
        if (delta <= 0) return java.util.Optional.empty();
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            int requiredProgress; int rewardCredits; int rewardDiamonds; int rewardNutPoints; String badgeCode;
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT required_progress, reward_credits, reward_diamonds, reward_nut_points, badge_code " +
                "FROM habnut_quests_def WHERE id = ?")) {
                ps.setLong(1, questId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { conn.rollback(); return java.util.Optional.empty(); }
                    requiredProgress = rs.getInt("required_progress");
                    rewardCredits    = rs.getInt("reward_credits");
                    rewardDiamonds   = rs.getInt("reward_diamonds");
                    rewardNutPoints  = rs.getInt("reward_nut_points");
                    badgeCode        = rs.getString("badge_code");
                }
            }

            // Must be accepted and not completed/abandoned
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT progress, completed_at FROM habnut_user_quests " +
                "WHERE user_id = ? AND quest_id = ? AND abandoned_at IS NULL FOR UPDATE")) {
                ps.setLong(1, userId); ps.setLong(2, questId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { conn.rollback(); return java.util.Optional.empty(); }
                    if (rs.getTimestamp("completed_at") != null) {
                        conn.rollback(); return java.util.Optional.empty();
                    }
                }
            }

            try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE habnut_user_quests SET progress = LEAST(progress + ?, ?) " +
                "WHERE user_id = ? AND quest_id = ? AND completed_at IS NULL")) {
                upd.setInt(1, delta); upd.setInt(2, requiredProgress);
                upd.setLong(3, userId); upd.setLong(4, questId);
                upd.executeUpdate();
            }

            int currentProgress;
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT progress FROM habnut_user_quests WHERE user_id = ? AND quest_id = ?")) {
                ps.setLong(1, userId); ps.setLong(2, questId);
                try (ResultSet rs = ps.executeQuery()) {
                    currentProgress = rs.next() ? rs.getInt("progress") : 0;
                }
            }

            boolean justCompleted = false;
            if (currentProgress >= requiredProgress) {
                try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE habnut_user_quests SET completed_at = NOW() " +
                    "WHERE user_id = ? AND quest_id = ? AND completed_at IS NULL")) {
                    upd.setLong(1, userId); upd.setLong(2, questId);
                    justCompleted = upd.executeUpdate() > 0;
                }
            }

            conn.commit();
            return java.util.Optional.of(new ProgressResult(justCompleted, currentProgress,
                justCompleted ? rewardCredits : 0,
                justCompleted ? rewardDiamonds : 0,
                justCompleted ? rewardNutPoints : 0,
                justCompleted ? badgeCode : null));
        } catch (SQLException e) {
            log.error("incrementProgress quest failed: user={} quest={}", userId, questId, e);
            return java.util.Optional.empty();
        }
    }

    private List<Quest> mapQuests(PreparedStatement ps) throws SQLException {
        List<Quest> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp expiresAt = rs.getTimestamp("expires_at");
                result.add(new Quest(
                    rs.getLong("id"), rs.getString("code"),
                    rs.getString("name"), rs.getString("description"),
                    rs.getString("category"), rs.getString("type"),
                    rs.getInt("reward_credits"), rs.getInt("reward_diamonds"),
                    rs.getInt("reward_nut_points"), rs.getString("badge_code"),
                    rs.getInt("progress"), rs.getInt("required_progress"),
                    rs.getBoolean("completed"),
                    expiresAt != null ? expiresAt.toString() : null));
            }
        }
        return result;
    }
}
