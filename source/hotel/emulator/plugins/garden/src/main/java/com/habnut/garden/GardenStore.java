package com.habnut.garden;

import com.eu.habbo.Emulator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The garden's tables: who planted what, harvest counts, and each week's goal. */
final class GardenStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(GardenStore.class);
    static final int DEFAULT_TARGET = 50;

    private GardenStore() {}

    private static Connection connection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    static void migrate() {
        String[] tables = {
            """
            CREATE TABLE IF NOT EXISTS habnut_garden_plants (
              item_id INT NOT NULL PRIMARY KEY,
              user_id INT NOT NULL,
              room_id INT NOT NULL,
              flower VARCHAR(70) NOT NULL,
              planted_at INT NOT NULL,
              KEY user_id (user_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
            """
            CREATE TABLE IF NOT EXISTS habnut_garden_gardeners (
              user_id INT NOT NULL PRIMARY KEY,
              harvests INT NOT NULL DEFAULT 0,
              planted INT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
            """
            CREATE TABLE IF NOT EXISTS habnut_garden_weeks (
              week VARCHAR(10) NOT NULL PRIMARY KEY,
              target INT NOT NULL,
              harvests INT NOT NULL DEFAULT 0,
              rewarded TINYINT(1) NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
            """
            CREATE TABLE IF NOT EXISTS habnut_garden_contributions (
              week VARCHAR(10) NOT NULL,
              user_id INT NOT NULL,
              harvests INT NOT NULL DEFAULT 0,
              PRIMARY KEY (week, user_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        };
        try (Connection c = connection(); Statement s = c.createStatement()) {
            for (String sql : tables) s.execute(sql);
        } catch (SQLException e) {
            LOGGER.error("[Garden] could not create its tables", e);
        }
    }

    private static int update(String sql, Object... args) {
        try (Connection c = connection(); PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
            return s.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[Garden] {} failed", sql, e);
            return 0;
        }
    }

    private static int[] ints(String sql, int columns, Object... args) {
        int[] out = new int[columns];
        try (Connection c = connection(); PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
            try (ResultSet r = s.executeQuery()) {
                if (r.next()) for (int i = 0; i < columns; i++) out[i] = r.getInt(i + 1);
            }
        } catch (SQLException e) {
            LOGGER.error("[Garden] {} failed", sql, e);
        }
        return out;
    }

    /** Living plants: planted by the garden and still in a room. */
    static int livingPlants(int userId) {
        return ints("SELECT COUNT(*) FROM habnut_garden_plants p JOIN items i ON i.id = p.item_id WHERE p.user_id = ? AND i.room_id > 0",
                1, userId)[0];
    }

    static void planted(int itemId, int userId, int roomId, String flower) {
        update("INSERT INTO habnut_garden_plants (item_id, user_id, room_id, flower, planted_at) VALUES (?, ?, ?, ?, ?)",
                itemId, userId, roomId, flower, Emulator.getIntUnixTimestamp());
        update("INSERT INTO habnut_garden_gardeners (user_id, planted) VALUES (?, 1) ON DUPLICATE KEY UPDATE planted = planted + 1",
                userId);
    }

    static void removed(int itemId) {
        update("DELETE FROM habnut_garden_plants WHERE item_id = ?", itemId);
    }

    /** Counts a harvest; returns the gardener's total. */
    static int harvested(int userId) {
        update("INSERT INTO habnut_garden_gardeners (user_id, harvests) VALUES (?, 1) ON DUPLICATE KEY UPDATE harvests = harvests + 1",
                userId);
        return ints("SELECT harvests FROM habnut_garden_gardeners WHERE user_id = ?", 1, userId)[0];
    }

    /** Counts a harvest towards the week's goal; returns {harvests, target}. */
    static int[] contribute(String week, int userId) {
        update("INSERT IGNORE INTO habnut_garden_weeks (week, target) VALUES (?, ?)", week, target());
        update("UPDATE habnut_garden_weeks SET harvests = harvests + 1 WHERE week = ?", week);
        update("INSERT INTO habnut_garden_contributions (week, user_id, harvests) VALUES (?, ?, 1)"
                + " ON DUPLICATE KEY UPDATE harvests = harvests + 1", week, userId);
        return goal(week);
    }

    static int[] goal(String week) {
        update("INSERT IGNORE INTO habnut_garden_weeks (week, target) VALUES (?, ?)", week, target());
        return ints("SELECT harvests, target FROM habnut_garden_weeks WHERE week = ?", 2, week);
    }

    static void setTarget(String week, int target) {
        update("INSERT INTO habnut_garden_weeks (week, target) VALUES (?, ?) ON DUPLICATE KEY UPDATE target = VALUES(target)",
                week, target);
    }

    /** True for exactly one caller: the goal pays out once. */
    static boolean markGoalRewarded(String week) {
        return update("UPDATE habnut_garden_weeks SET rewarded = 1 WHERE week = ? AND rewarded = 0", week) == 1;
    }

    static List<Integer> contributors(String week) {
        List<Integer> ids = new ArrayList<>();
        try (Connection c = connection();
                PreparedStatement s = c.prepareStatement("SELECT user_id FROM habnut_garden_contributions WHERE week = ?")) {
            s.setString(1, week);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) ids.add(r.getInt(1));
            }
        } catch (SQLException e) {
            LOGGER.error("[Garden] could not list gardeners", e);
        }
        return ids;
    }

    /** {harvests, growing} */
    static int[] stats(int userId) {
        return new int[] {ints("SELECT harvests FROM habnut_garden_gardeners WHERE user_id = ?", 1, userId)[0], livingPlants(userId)};
    }

    static void rewardOffline(int userId, int credits, String badge) {
        update("UPDATE users SET credits = credits + ? WHERE id = ?", credits, userId);
        update("INSERT INTO users_badges (user_id, slot_id, badge_code) SELECT ?, 0, ? FROM DUAL"
                + " WHERE NOT EXISTS (SELECT 1 FROM users_badges WHERE user_id = ? AND badge_code = ?)", userId, badge, userId, badge);
    }

    /** Harvests give herbs to the gardener's Nutropolis pockets, for crafting there. */
    static void herbsForNutropolis(int userId) {
        update("INSERT INTO habnut_rp_inventory (user_id, item, qty) VALUES (?, 'herbs', 1) ON DUPLICATE KEY UPDATE qty = qty + 1", userId);
    }

    private static int target() {
        return Emulator.getConfig().getInt("habnut.garden.weekly.target", DEFAULT_TARGET);
    }
}
