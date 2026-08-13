package com.habnut.emulator.progression;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class BadgeService {

    private static final Logger log = LoggerFactory.getLogger(BadgeService.class);
    private static final int MAX_EQUIPPED = 5;

    public record Badge(String code, String name, String description, String imageUrl,
                        String earnedAt, boolean equipped, int slotIndex) {}

    private final DatabaseManager db;

    public BadgeService(DatabaseManager db) {
        this.db = db;
    }

    public List<Badge> getBadges(long userId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT ub.badge_code, bd.name, bd.description, bd.image_url, " +
                 "ub.earned_at, ub.equipped, ub.slot_index " +
                 "FROM habnut_user_badges ub " +
                 "JOIN habnut_badges_def bd ON bd.code = ub.badge_code " +
                 "WHERE ub.user_id = ? ORDER BY ub.equipped DESC, ub.slot_index ASC, ub.earned_at DESC")) {
            ps.setLong(1, userId);
            List<Badge> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Badge(
                        rs.getString("badge_code"), rs.getString("name"),
                        rs.getString("description"), rs.getString("image_url"),
                        rs.getTimestamp("earned_at").toString(),
                        rs.getBoolean("equipped"), rs.getInt("slot_index")));
                }
            }
            return result;
        } catch (SQLException e) {
            log.error("getBadges failed for user {}", userId, e);
            return List.of();
        }
    }

    public boolean grant(long userId, String badgeCode) {
        try (Connection conn = db.getConnection();
             PreparedStatement ins = conn.prepareStatement(
                 "INSERT IGNORE INTO habnut_user_badges (user_id, badge_code) VALUES (?, ?)")) {
            ins.setLong(1, userId); ins.setString(2, badgeCode);
            return ins.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("grant badge failed: user={} badge={}", userId, badgeCode, e);
            return false;
        }
    }

    public boolean hasBadge(long userId, String badgeCode) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT 1 FROM habnut_user_badges WHERE user_id = ? AND badge_code = ?")) {
            ps.setLong(1, userId); ps.setString(2, badgeCode);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            log.error("hasBadge check failed", e);
            return false;
        }
    }

    public boolean equip(long userId, String badgeCode, int slotIndex) {
        if (slotIndex < 1 || slotIndex > MAX_EQUIPPED) return false;
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            // Clear the slot first
            try (PreparedStatement clr = conn.prepareStatement(
                "UPDATE habnut_user_badges SET equipped = 0, slot_index = 0 " +
                "WHERE user_id = ? AND slot_index = ?")) {
                clr.setLong(1, userId); clr.setInt(2, slotIndex);
                clr.executeUpdate();
            }
            try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE habnut_user_badges SET equipped = 1, slot_index = ? " +
                "WHERE user_id = ? AND badge_code = ?")) {
                upd.setInt(1, slotIndex); upd.setLong(2, userId); upd.setString(3, badgeCode);
                boolean ok = upd.executeUpdate() > 0;
                if (!ok) { conn.rollback(); return false; }
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            log.error("equip badge failed: user={} badge={}", userId, badgeCode, e);
            return false;
        }
    }

    public boolean unequip(long userId, String badgeCode) {
        try (Connection conn = db.getConnection();
             PreparedStatement upd = conn.prepareStatement(
                 "UPDATE habnut_user_badges SET equipped = 0, slot_index = 0 " +
                 "WHERE user_id = ? AND badge_code = ?")) {
            upd.setLong(1, userId); upd.setString(2, badgeCode);
            return upd.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("unequip badge failed", e);
            return false;
        }
    }
}
