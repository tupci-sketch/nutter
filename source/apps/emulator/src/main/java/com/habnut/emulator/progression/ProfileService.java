package com.habnut.emulator.progression;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    public record Profile(long userId, String username, String figure, String motto,
                          int credits, int diamonds, int nutPoints,
                          int level, int xp, int xpToNextLevel,
                          int roomsOwned, int friendCount, int achievementPoints,
                          String memberSince, String lastSeen, boolean online,
                          List<BadgeService.Badge> equippedBadges) {}

    private static final int[] XP_PER_LEVEL = buildXpTable(50);

    private final DatabaseManager db;

    public ProfileService(DatabaseManager db) {
        this.db = db;
    }

    public Optional<Profile> getProfile(long targetUserId, BadgeService badgeService) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT u.id, u.username, u.figure, u.motto, " +
                 "u.credits, u.diamonds, u.nut_points, u.xp, " +
                 "u.member_since, u.last_seen, u.online, " +
                 "(SELECT COUNT(*) FROM habnut_rooms WHERE owner_id = u.id) AS rooms_owned, " +
                 "(SELECT COUNT(*) FROM habnut_friends WHERE (user_a = u.id OR user_b = u.id) AND accepted = 1) AS friend_count, " +
                 "(SELECT COALESCE(SUM(ad.points), 0) FROM habnut_user_achievements ua " +
                 " JOIN habnut_achievements ad ON ad.code = ua.achievement_code " +
                 " WHERE ua.user_id = u.id AND ua.completed_at IS NOT NULL) AS achievement_points " +
                 "FROM habnut_users u WHERE u.id = ?")) {
            ps.setLong(1, targetUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                int xp    = rs.getInt("xp");
                int level = levelFromXp(xp);
                int xpForLevel    = xp - xpForLevel(level);
                int xpToNextLevel = xpForLevel(level + 1) - xpForLevel(level);
                List<BadgeService.Badge> equipped = badgeService.getBadges(targetUserId).stream()
                    .filter(BadgeService.Badge::equipped)
                    .sorted(java.util.Comparator.comparingInt(BadgeService.Badge::slotIndex))
                    .toList();
                Timestamp lastSeen = rs.getTimestamp("last_seen");
                Timestamp memberSince = rs.getTimestamp("member_since");
                return Optional.of(new Profile(
                    rs.getLong("id"), rs.getString("username"),
                    rs.getString("figure"), rs.getString("motto"),
                    rs.getInt("credits"), rs.getInt("diamonds"), rs.getInt("nut_points"),
                    level, xpForLevel, xpToNextLevel,
                    rs.getInt("rooms_owned"), rs.getInt("friend_count"),
                    rs.getInt("achievement_points"),
                    memberSince != null ? memberSince.toString() : null,
                    lastSeen != null ? lastSeen.toString() : null,
                    rs.getBoolean("online"), equipped));
            }
        } catch (SQLException e) {
            log.error("getProfile failed: user={}", targetUserId, e);
            return Optional.empty();
        }
    }

    public boolean updateMotto(long userId, String motto) {
        if (motto == null) return false;
        motto = motto.trim();
        if (motto.length() > 128) motto = motto.substring(0, 128);
        try (Connection conn = db.getConnection();
             PreparedStatement upd = conn.prepareStatement(
                 "UPDATE habnut_users SET motto = ? WHERE id = ?")) {
            upd.setString(1, motto); upd.setLong(2, userId);
            return upd.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("updateMotto failed: user={}", userId, e);
            return false;
        }
    }

    public boolean updateFigure(long userId, String figure) {
        if (figure == null || figure.isBlank()) return false;
        try (Connection conn = db.getConnection();
             PreparedStatement upd = conn.prepareStatement(
                 "UPDATE habnut_users SET figure = ? WHERE id = ?")) {
            upd.setString(1, figure.trim()); upd.setLong(2, userId);
            return upd.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("updateFigure failed: user={}", userId, e);
            return false;
        }
    }

    public boolean grantXp(long userId, int amount) {
        if (amount <= 0) return false;
        try (Connection conn = db.getConnection();
             PreparedStatement upd = conn.prepareStatement(
                 "UPDATE habnut_users SET xp = xp + ?, nut_points = nut_points + ? WHERE id = ?")) {
            upd.setInt(1, amount); upd.setInt(2, Math.max(1, amount / 10));
            upd.setLong(3, userId);
            return upd.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("grantXp failed: user={}", userId, e);
            return false;
        }
    }

    public static int levelFromXp(int xp) {
        for (int i = XP_PER_LEVEL.length - 1; i >= 0; i--) {
            if (xp >= XP_PER_LEVEL[i]) return i + 1;
        }
        return 1;
    }

    public static int xpForLevel(int level) {
        int idx = Math.min(level - 1, XP_PER_LEVEL.length - 1);
        return idx < 0 ? 0 : XP_PER_LEVEL[idx];
    }

    private static int[] buildXpTable(int maxLevel) {
        int[] table = new int[maxLevel];
        table[0] = 0;
        for (int i = 1; i < maxLevel; i++) {
            table[i] = (int) (table[i - 1] + 100 * Math.pow(1.15, i));
        }
        return table;
    }
}
