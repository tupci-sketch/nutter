package com.habnut.emulator.progression;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AchievementService {

    private static final Logger log = LoggerFactory.getLogger(AchievementService.class);

    public record AchievementDef(String code, String name, String description,
                                 String category, int points, int maxProgress,
                                 String badgeCode) {}

    public record UserAchievement(String code, String name, String description,
                                  String category, int points, int progress,
                                  int maxProgress, boolean completed, String completedAt) {}

    private final DatabaseManager db;
    private final BadgeService badgeService;

    public AchievementService(DatabaseManager db, BadgeService badgeService) {
        this.db           = db;
        this.badgeService = badgeService;
    }

    public List<UserAchievement> getForUser(long userId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT ad.code, ad.name, ad.description, ad.category, ad.points, " +
                 "ad.max_progress, COALESCE(up.progress, 0) as progress, " +
                 "up.completed_at " +
                 "FROM habnut_achievements_def ad " +
                 "LEFT JOIN habnut_user_achievements up " +
                 "  ON up.achievement_code = ad.code AND up.user_id = ? " +
                 "ORDER BY ad.category, ad.code")) {
            ps.setLong(1, userId);
            List<UserAchievement> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp completedAt = rs.getTimestamp("completed_at");
                    int progress    = rs.getInt("progress");
                    int maxProgress = rs.getInt("max_progress");
                    result.add(new UserAchievement(
                        rs.getString("code"), rs.getString("name"),
                        rs.getString("description"), rs.getString("category"),
                        rs.getInt("points"), progress, maxProgress,
                        completedAt != null, completedAt != null ? completedAt.toString() : null));
                }
            }
            return result;
        } catch (SQLException e) {
            log.error("getForUser achievements failed: user={}", userId, e);
            return List.of();
        }
    }

    public record ProgressResult(boolean wasNew, boolean justCompleted, int newProgress) {}

    public Optional<ProgressResult> incrementProgress(long userId, String achievementCode, int delta) {
        if (delta <= 0) return Optional.empty();
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            // Load achievement definition
            int maxProgress;
            String badgeCode;
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT max_progress, badge_code FROM habnut_achievements_def WHERE code = ?")) {
                ps.setString(1, achievementCode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { conn.rollback(); return Optional.empty(); }
                    maxProgress = rs.getInt("max_progress");
                    badgeCode   = rs.getString("badge_code");
                }
            }

            // Insert or update progress row
            try (PreparedStatement ups = conn.prepareStatement(
                "INSERT INTO habnut_user_achievements (user_id, achievement_code, progress) " +
                "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "progress = IF(completed_at IS NOT NULL, progress, LEAST(progress + ?, ?))")) {
                ups.setLong(1, userId); ups.setString(2, achievementCode);
                ups.setInt(3, Math.min(delta, maxProgress));
                ups.setInt(4, delta); ups.setInt(5, maxProgress);
                ups.executeUpdate();
            }

            // Read current state
            int currentProgress;
            boolean alreadyCompleted;
            try (PreparedStatement sel = conn.prepareStatement(
                "SELECT progress, completed_at FROM habnut_user_achievements " +
                "WHERE user_id = ? AND achievement_code = ?")) {
                sel.setLong(1, userId); sel.setString(2, achievementCode);
                try (ResultSet rs = sel.executeQuery()) {
                    if (!rs.next()) { conn.rollback(); return Optional.empty(); }
                    currentProgress  = rs.getInt("progress");
                    alreadyCompleted = rs.getTimestamp("completed_at") != null;
                }
            }

            boolean justCompleted = false;
            if (!alreadyCompleted && currentProgress >= maxProgress) {
                try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE habnut_user_achievements SET completed_at = NOW() " +
                    "WHERE user_id = ? AND achievement_code = ? AND completed_at IS NULL")) {
                    upd.setLong(1, userId); upd.setString(2, achievementCode);
                    justCompleted = upd.executeUpdate() > 0;
                }
                if (justCompleted && badgeCode != null && !badgeCode.isBlank()) {
                    badgeService.grant(userId, badgeCode);
                }
            }

            conn.commit();
            log.debug("Achievement progress: user={} code={} progress={}/{} completed={}",
                userId, achievementCode, currentProgress, maxProgress, justCompleted);
            return Optional.of(new ProgressResult(false, justCompleted, currentProgress));
        } catch (SQLException e) {
            log.error("incrementProgress failed: user={} code={}", userId, achievementCode, e);
            return Optional.empty();
        }
    }
}
