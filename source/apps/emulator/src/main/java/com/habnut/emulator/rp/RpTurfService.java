package com.habnut.emulator.rp;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Territory that factions hold and fight over.
 *
 * A capture is a timed contest rather than an instant claim: a faction starts
 * one, and it resolves only when the timer elapses. That gives the holding
 * faction a window to contest it, which is the point of territory.
 */
public final class RpTurfService {

    private static final Logger log = LoggerFactory.getLogger(RpTurfService.class);

    public record Turf(int id, String code, String name, String description,
                       Long roomId, int incomePerHour, int captureSeconds,
                       Long ownerFactionId, String ownerFactionName, String capturedAt) {}

    public record Capture(long id, int turfId, int factionId, long startedBy,
                          String startedAt, String completesAt, String outcome) {}

    private final DatabaseManager db;

    public RpTurfService(DatabaseManager db) {
        this.db = db;
    }

    public List<Turf> list() {
        List<Turf> turfs = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT t.id, t.code, t.name, t.description, t.room_id, t.income_per_hour, " +
                 "t.capture_seconds, t.owner_faction_id, f.name AS owner_name, t.captured_at " +
                 "FROM habnut_rp_turfs t " +
                 "LEFT JOIN habnut_rp_factions f ON f.id = t.owner_faction_id " +
                 "ORDER BY t.name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                turfs.add(new Turf(
                    rs.getInt("id"), rs.getString("code"), rs.getString("name"),
                    rs.getString("description"),
                    rs.getObject("room_id") == null ? null : rs.getLong("room_id"),
                    rs.getInt("income_per_hour"), rs.getInt("capture_seconds"),
                    rs.getObject("owner_faction_id") == null ? null : rs.getLong("owner_faction_id"),
                    rs.getString("owner_name"),
                    String.valueOf(rs.getTimestamp("captured_at"))));
            }
        } catch (SQLException e) {
            log.error("turf list failed", e);
        }
        return turfs;
    }

    /**
     * Begins a capture attempt.
     *
     * Refused if the faction already holds the turf, or if a contest is already
     * running — one contest at a time keeps the outcome unambiguous.
     */
    public String beginCapture(int turfId, int factionId, long characterId) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            int captureSeconds;
            try (PreparedStatement ps = conn.prepareStatement(
                     "SELECT owner_faction_id, capture_seconds FROM habnut_rp_turfs " +
                     "WHERE id = ? FOR UPDATE")) {
                ps.setInt(1, turfId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { conn.rollback(); return "That territory does not exist"; }
                    Object owner = rs.getObject("owner_faction_id");
                    if (owner != null && rs.getInt("owner_faction_id") == factionId) {
                        conn.rollback();
                        return "Your faction already holds this territory";
                    }
                    captureSeconds = rs.getInt("capture_seconds");
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM habnut_rp_turf_captures " +
                     "WHERE turf_id = ? AND outcome = 'pending'")) {
                ps.setInt(1, turfId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) { conn.rollback(); return "This territory is already being contested"; }
                }
            }

            try (PreparedStatement ins = conn.prepareStatement(
                     "INSERT INTO habnut_rp_turf_captures " +
                     "(turf_id, faction_id, started_by, completes_at) " +
                     "VALUES (?, ?, ?, DATE_ADD(NOW(), INTERVAL ? SECOND))")) {
                ins.setInt(1, turfId);
                ins.setInt(2, factionId);
                ins.setLong(3, characterId);
                ins.setInt(4, captureSeconds);
                ins.executeUpdate();
            }

            conn.commit();
            return null;
        } catch (SQLException e) {
            log.error("beginCapture failed: turf={} faction={}", turfId, factionId, e);
            return "Capture could not be started";
        }
    }

    /** Abandons a running capture, which leaves the holder in place. */
    public boolean abandonCapture(int turfId, int factionId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_rp_turf_captures SET outcome = 'abandoned', resolved_at = NOW() " +
                 "WHERE turf_id = ? AND faction_id = ? AND outcome = 'pending'")) {
            ps.setInt(1, turfId);
            ps.setInt(2, factionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("abandonCapture failed: turf={} faction={}", turfId, factionId, e);
            return false;
        }
    }

    /**
     * Resolves every capture whose timer has elapsed, transferring ownership.
     *
     * Called on a schedule rather than by a client, so a contest completes even
     * if everyone involved has disconnected.
     *
     * @return the number of territories that changed hands
     */
    public int resolveElapsedCaptures() {
        int transferred = 0;
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            List<long[]> due = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, turf_id, faction_id FROM habnut_rp_turf_captures " +
                     "WHERE outcome = 'pending' AND completes_at <= NOW() FOR UPDATE");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    due.add(new long[]{rs.getLong("id"), rs.getInt("turf_id"), rs.getInt("faction_id")});
                }
            }

            for (long[] row : due) {
                try (PreparedStatement upd = conn.prepareStatement(
                         "UPDATE habnut_rp_turfs SET owner_faction_id = ?, captured_at = NOW() " +
                         "WHERE id = ?")) {
                    upd.setLong(1, row[2]);
                    upd.setLong(2, row[1]);
                    upd.executeUpdate();
                }
                try (PreparedStatement done = conn.prepareStatement(
                         "UPDATE habnut_rp_turf_captures SET outcome = 'captured', resolved_at = NOW() " +
                         "WHERE id = ?")) {
                    done.setLong(1, row[0]);
                    done.executeUpdate();
                }
                transferred++;
            }

            conn.commit();
        } catch (SQLException e) {
            log.error("resolveElapsedCaptures failed", e);
        }
        return transferred;
    }

    /** Territories a faction currently holds. */
    public List<Turf> heldBy(int factionId) {
        return list().stream()
            .filter(t -> t.ownerFactionId() != null && t.ownerFactionId() == factionId)
            .toList();
    }

    /** Total hourly income from every territory a faction holds. */
    public int hourlyIncome(int factionId) {
        return heldBy(factionId).stream().mapToInt(Turf::incomePerHour).sum();
    }
}
