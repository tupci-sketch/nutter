package com.habnut.nutropolis;

import com.eu.habbo.Emulator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Nutropolis's tables. The city keeps its own; nothing here writes to the hotel's economy. */
final class Store {
    private static final Logger LOGGER = LoggerFactory.getLogger(Store.class);

    private Store() {}

    private static Connection connection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    static void migrate() {
        String[] tables = {
            """
            CREATE TABLE IF NOT EXISTS habnut_rp_citizens (
              user_id INT NOT NULL PRIMARY KEY,
              cash BIGINT NOT NULL DEFAULT 0,
              bank BIGINT NOT NULL DEFAULT 0,
              health INT NOT NULL DEFAULT 100,
              job_id INT NOT NULL DEFAULT 0,
              job_rank INT NOT NULL DEFAULT 0,
              wanted INT NOT NULL DEFAULT 0,
              jailed_until INT NOT NULL DEFAULT 0,
              shifts INT NOT NULL DEFAULT 0,
              created_at INT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
            """
            CREATE TABLE IF NOT EXISTS habnut_rp_jobs (
              id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
              code VARCHAR(32) NOT NULL UNIQUE,
              name VARCHAR(64) NOT NULL,
              kind VARCHAR(16) NOT NULL DEFAULT 'civil',
              room_id INT NOT NULL DEFAULT 0,
              wage INT NOT NULL DEFAULT 25,
              shift_minutes INT NOT NULL DEFAULT 10,
              ranks VARCHAR(255) NOT NULL DEFAULT '',
              vehicle_effect INT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
            """
            CREATE TABLE IF NOT EXISTS habnut_rp_rooms (
              room_id INT NOT NULL PRIMARY KEY,
              kind VARCHAR(16) NOT NULL,
              safe TINYINT(1) NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
            // Every change to a citizen's money, never updated or deleted.
            """
            CREATE TABLE IF NOT EXISTS habnut_rp_ledger (
              id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
              user_id INT NOT NULL,
              account ENUM('cash','bank') NOT NULL,
              delta BIGINT NOT NULL,
              balance BIGINT NOT NULL,
              reason VARCHAR(32) NOT NULL,
              other_user_id INT NOT NULL DEFAULT 0,
              created_at INT NOT NULL,
              KEY user_time (user_id, created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
            """
            CREATE TABLE IF NOT EXISTS habnut_rp_records (
              id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
              user_id INT NOT NULL,
              officer_id INT NOT NULL,
              offence VARCHAR(128) NOT NULL,
              minutes INT NOT NULL,
              created_at INT NOT NULL,
              KEY user_time (user_id, created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        };
        try (Connection c = connection(); Statement s = c.createStatement()) {
            for (String sql : tables) s.execute(sql);
        } catch (SQLException e) {
            LOGGER.error("[Nutropolis] could not create its tables", e);
        }
    }

    static Citizen load(int userId) {
        try (Connection c = connection()) {
            try (PreparedStatement s = c.prepareStatement("SELECT * FROM habnut_rp_citizens WHERE user_id = ?")) {
                s.setInt(1, userId);
                try (ResultSet r = s.executeQuery()) {
                    if (r.next()) {
                        Citizen citizen = new Citizen(userId);
                        citizen.cash = r.getLong("cash");
                        citizen.bank = r.getLong("bank");
                        citizen.health = r.getInt("health");
                        citizen.jobId = r.getInt("job_id");
                        citizen.jobRank = r.getInt("job_rank");
                        citizen.wanted = r.getInt("wanted");
                        citizen.jailedUntil = r.getInt("jailed_until");
                        citizen.shifts = r.getInt("shifts");
                        return citizen;
                    }
                }
            }
            Citizen citizen = new Citizen(userId);
            citizen.cash = Nutropolis.START_CASH;
            try (PreparedStatement s = c.prepareStatement(
                    "INSERT IGNORE INTO habnut_rp_citizens (user_id, cash, created_at) VALUES (?, ?, ?)")) {
                s.setInt(1, userId);
                s.setLong(2, citizen.cash);
                s.setInt(3, Nutropolis.now());
                s.execute();
            }
            ledger(c, userId, "cash", citizen.cash, citizen.cash, "arrival", 0);
            return citizen;
        } catch (SQLException e) {
            LOGGER.error("[Nutropolis] could not load citizen {}", userId, e);
            return null;
        }
    }

    static void save(Citizen citizen) {
        try (Connection c = connection();
                PreparedStatement s = c.prepareStatement(
                        "UPDATE habnut_rp_citizens SET cash = ?, bank = ?, health = ?, job_id = ?, job_rank = ?, "
                                + "wanted = ?, jailed_until = ?, shifts = ? WHERE user_id = ?")) {
            s.setLong(1, citizen.cash);
            s.setLong(2, citizen.bank);
            s.setInt(3, citizen.health);
            s.setInt(4, citizen.jobId);
            s.setInt(5, citizen.jobRank);
            s.setInt(6, citizen.wanted);
            s.setInt(7, citizen.jailedUntil);
            s.setInt(8, citizen.shifts);
            s.setInt(9, citizen.userId);
            s.execute();
        } catch (SQLException e) {
            LOGGER.error("[Nutropolis] could not save citizen {}", citizen.userId, e);
        }
    }

    static void ledger(int userId, String account, long delta, long balance, String reason, int otherUserId) {
        try (Connection c = connection()) {
            ledger(c, userId, account, delta, balance, reason, otherUserId);
        } catch (SQLException e) {
            LOGGER.error("[Nutropolis] could not write the ledger for {}", userId, e);
        }
    }

    private static void ledger(Connection c, int userId, String account, long delta, long balance, String reason,
            int otherUserId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "INSERT INTO habnut_rp_ledger (user_id, account, delta, balance, reason, other_user_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            s.setInt(1, userId);
            s.setString(2, account);
            s.setLong(3, delta);
            s.setLong(4, balance);
            s.setString(5, reason);
            s.setInt(6, otherUserId);
            s.setInt(7, Nutropolis.now());
            s.execute();
        }
    }

    static void record(int userId, int officerId, String offence, int minutes) {
        try (Connection c = connection();
                PreparedStatement s = c.prepareStatement(
                        "INSERT INTO habnut_rp_records (user_id, officer_id, offence, minutes, created_at) "
                                + "VALUES (?, ?, ?, ?, ?)")) {
            s.setInt(1, userId);
            s.setInt(2, officerId);
            s.setString(3, offence.length() > 128 ? offence.substring(0, 128) : offence);
            s.setInt(4, minutes);
            s.setInt(5, Nutropolis.now());
            s.execute();
        } catch (SQLException e) {
            LOGGER.error("[Nutropolis] could not write a record for {}", userId, e);
        }
    }

    static int recordCount(int userId) {
        try (Connection c = connection();
                PreparedStatement s = c.prepareStatement("SELECT COUNT(*) FROM habnut_rp_records WHERE user_id = ?")) {
            s.setInt(1, userId);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? r.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    static List<Job> jobs() {
        List<Job> jobs = new ArrayList<>();
        try (Connection c = connection();
                Statement s = c.createStatement();
                ResultSet r = s.executeQuery("SELECT * FROM habnut_rp_jobs ORDER BY id")) {
            while (r.next()) {
                String ranks = r.getString("ranks");
                jobs.add(new Job(r.getInt("id"), r.getString("code"), r.getString("name"), r.getString("kind"),
                        r.getInt("room_id"), r.getInt("wage"), Math.max(1, r.getInt("shift_minutes")),
                        ranks.isBlank() ? new String[0] : ranks.split(";"), r.getInt("vehicle_effect")));
            }
        } catch (SQLException e) {
            LOGGER.error("[Nutropolis] could not load jobs", e);
        }
        return jobs;
    }

    /** Room id to {kind, safe}. */
    static Map<Integer, String[]> rooms() {
        Map<Integer, String[]> rooms = new HashMap<>();
        try (Connection c = connection();
                Statement s = c.createStatement();
                ResultSet r = s.executeQuery("SELECT room_id, kind, safe FROM habnut_rp_rooms")) {
            while (r.next()) rooms.put(r.getInt(1), new String[] {r.getString(2), r.getInt(3) == 1 ? "1" : "0"});
        } catch (SQLException e) {
            LOGGER.error("[Nutropolis] could not load city rooms", e);
        }
        return rooms;
    }
}
