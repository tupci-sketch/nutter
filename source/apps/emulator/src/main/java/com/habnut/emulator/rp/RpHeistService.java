package com.habnut.emulator.rp;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Robbing something, as a crew.
 *
 * A heist is deliberately a contest rather than a button. A crew starts one, an
 * alarm reaches the police partway through, and the crew has to hold the target
 * until the timer runs out. Police who get there first stop it. Everything that
 * decides the outcome — the timings, the payout, whether it succeeded — is
 * settled here, because a client that reported its own heist would never lose
 * one.
 *
 * A target will not open at all unless enough police are on duty. A hotel with
 * nobody policing should not be free money; it should be a city where the banks
 * are shut.
 */
public final class RpHeistService {

    private static final Logger log = LoggerFactory.getLogger(RpHeistService.class);

    public record Target(int id, String code, String name, String description, Long roomId,
                         int minCrew, int maxCrew, int durationSeconds, int alarmSeconds,
                         int payoutMin, int payoutMax, int policeRequired,
                         int cooldownMinutes, boolean available, String unavailableReason) {}

    public record Heist(long id, int targetId, String targetName, int factionId,
                        long leaderCharacterId, String state, String startedAt,
                        String alarmAt, String resolvesAt, int payout, int crewSize) {}

    public record Result(boolean ok, String reason, Long heistId) {
        static Result no(String reason) { return new Result(false, reason, null); }
        static Result yes(long id) { return new Result(true, null, id); }
    }

    /** What a resolved heist paid, and to whom. */
    public record Payout(long heistId, int factionId, int total, int factionShare,
                         List<long[]> crewShares) {}

    private final DatabaseManager db;
    private final RpTreasuryService treasury;
    private final RpCharacterService characters;

    /**
     * The share of a heist that goes to the faction rather than the crew.
     *
     * Enough that holding territory and running jobs builds something, not so
     * much that turning up is not worth a member's time.
     */
    private static final double FACTION_SHARE = 0.35;

    public RpHeistService(DatabaseManager db, RpTreasuryService treasury,
                          RpCharacterService characters) {
        this.db = db;
        this.treasury = treasury;
        this.characters = characters;
    }

    // ─── what can be robbed ─────────────────────────────────────────────────

    /** Every target, with whether it can be attempted right now and why not. */
    public List<Target> targets(int policeOnDuty) {
        List<Target> targets = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT t.*, "
                 + "(t.last_robbed_at IS NOT NULL AND "
                 + " TIMESTAMPADD(MINUTE, t.cooldown_minutes, t.last_robbed_at) > NOW()) "
                 + "AS on_cooldown, "
                 + "EXISTS (SELECT 1 FROM habnut_rp_heists h WHERE h.target_id = t.id "
                 + "        AND h.state IN ('planning','in_progress')) AS busy "
                 + "FROM habnut_rp_heist_targets t WHERE t.enabled = 1 "
                 + "ORDER BY t.payout_max")) {

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean cooling = rs.getBoolean("on_cooldown");
                    boolean busy = rs.getBoolean("busy");
                    int policeRequired = rs.getInt("police_required");

                    String reason = null;
                    if (busy) reason = "already_being_robbed";
                    else if (cooling) reason = "too_soon_since_the_last_one";
                    else if (policeOnDuty < policeRequired) reason = "not_enough_police_on_duty";

                    targets.add(new Target(
                        rs.getInt("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("description"),
                        rs.getObject("room_id") == null ? null : rs.getLong("room_id"),
                        rs.getInt("min_crew"), rs.getInt("max_crew"),
                        rs.getInt("duration_seconds"), rs.getInt("alarm_seconds"),
                        rs.getInt("payout_min"), rs.getInt("payout_max"),
                        policeRequired, rs.getInt("cooldown_minutes"),
                        reason == null, reason));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to list heist targets", e);
        }
        return targets;
    }

    // ─── planning ───────────────────────────────────────────────────────────

    /**
     * Opens a job for a crew to join.
     *
     * Nothing happens yet: the crew has to gather first, which is the point of
     * the planning stage.
     */
    public Result plan(int targetId, int factionId, long leaderCharacterId, int policeOnDuty) {
        Optional<Target> target = targets(policeOnDuty).stream()
            .filter(t -> t.id() == targetId).findFirst();

        if (target.isEmpty()) return Result.no("no_such_target");
        if (!target.get().available()) return Result.no(target.get().unavailableReason());

        // One job at a time per faction: a faction splitting itself across three
        // banks is not a heist, it is a way of avoiding the police response.
        if (hasOpenHeist(factionId)) return Result.no("faction_already_has_a_job_open");

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_rp_heists (target_id, faction_id, leader_character_id) "
                 + "VALUES (?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, targetId);
            ps.setInt(2, factionId);
            ps.setLong(3, leaderCharacterId);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) return Result.no("error");
                long heistId = keys.getLong(1);
                join(heistId, leaderCharacterId);
                return Result.yes(heistId);
            }
        } catch (SQLException e) {
            log.error("Failed to plan a heist on target {}", targetId, e);
            return Result.no("error");
        }
    }

    /** Adds a character to a crew, while the job is still being planned. */
    public Result join(long heistId, long characterId) {
        try (Connection conn = db.getConnection()) {
            String state = stateOf(conn, heistId);
            if (state == null) return Result.no("no_such_heist");
            if (!"planning".equals(state)) return Result.no("job_already_started");

            int max = maxCrewFor(conn, heistId);
            if (crewSize(conn, heistId) >= max) return Result.no("crew_is_full");

            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT IGNORE INTO habnut_rp_heist_crew (heist_id, character_id) VALUES (?,?)")) {
                ps.setLong(1, heistId);
                ps.setLong(2, characterId);
                ps.executeUpdate();
            }
            return Result.yes(heistId);
        } catch (SQLException e) {
            log.error("Failed to add {} to heist {}", characterId, heistId, e);
            return Result.no("error");
        }
    }

    /** Removes a character from a crew before the job starts. */
    public Result leave(long heistId, long characterId) {
        try (Connection conn = db.getConnection()) {
            String state = stateOf(conn, heistId);
            if (state == null) return Result.no("no_such_heist");
            if (!"planning".equals(state)) return Result.no("job_already_started");

            try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM habnut_rp_heist_crew WHERE heist_id = ? AND character_id = ?")) {
                ps.setLong(1, heistId);
                ps.setLong(2, characterId);
                ps.executeUpdate();
            }

            // A job with nobody left on it is not a job.
            if (crewSize(conn, heistId) == 0) abandon(heistId);
            return Result.yes(heistId);
        } catch (SQLException e) {
            log.error("Failed to remove {} from heist {}", characterId, heistId, e);
            return Result.no("error");
        }
    }

    // ─── the job ────────────────────────────────────────────────────────────

    /**
     * Starts the job.
     *
     * From here the clock is the server's. The alarm and the finish are both
     * stamped now rather than counted down, so a lagging client, a reconnect or
     * a restart all resolve to the same moment.
     */
    public Result start(long heistId, long byCharacterId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT h.state, h.leader_character_id, t.min_crew, "
                 + "t.duration_seconds, t.alarm_seconds "
                 + "FROM habnut_rp_heists h JOIN habnut_rp_heist_targets t ON t.id = h.target_id "
                 + "WHERE h.id = ?")) {
            ps.setLong(1, heistId);

            int minCrew, duration, alarm;
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Result.no("no_such_heist");
                if (!"planning".equals(rs.getString("state"))) return Result.no("job_already_started");
                if (rs.getLong("leader_character_id") != byCharacterId) {
                    return Result.no("only_the_leader_starts_the_job");
                }
                minCrew = rs.getInt("min_crew");
                duration = rs.getInt("duration_seconds");
                alarm = rs.getInt("alarm_seconds");
            }

            if (crewSize(conn, heistId) < minCrew) return Result.no("not_enough_crew");

            try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE habnut_rp_heists SET state = 'in_progress', started_at = NOW(), "
                + "alarm_at = TIMESTAMPADD(SECOND, ?, NOW()), "
                + "resolves_at = TIMESTAMPADD(SECOND, ?, NOW()) WHERE id = ?")) {
                upd.setInt(1, alarm);
                upd.setInt(2, duration);
                upd.setLong(3, heistId);
                upd.executeUpdate();
            }
            return Result.yes(heistId);
        } catch (SQLException e) {
            log.error("Failed to start heist {}", heistId, e);
            return Result.no("error");
        }
    }

    /**
     * Stops a job in progress.
     *
     * Only worth anything after the alarm has gone: police who happen to be
     * standing in the bank before anybody knows a robbery is happening have not
     * responded to it.
     */
    public Result foil(long heistId, long byCharacterId) {
        try (Connection conn = db.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT state, alarm_at <= NOW() AS alarm_raised "
                + "FROM habnut_rp_heists WHERE id = ?")) {
                ps.setLong(1, heistId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Result.no("no_such_heist");
                    if (!"in_progress".equals(rs.getString("state"))) {
                        return Result.no("nothing_to_stop");
                    }
                    if (!rs.getBoolean("alarm_raised")) return Result.no("alarm_has_not_gone_yet");
                }
            }

            try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE habnut_rp_heists SET state = 'foiled', ended_at = NOW(), "
                + "foiled_by_character_id = ? WHERE id = ? AND state = 'in_progress'")) {
                upd.setLong(1, byCharacterId);
                upd.setLong(2, heistId);
                if (upd.executeUpdate() == 0) return Result.no("nothing_to_stop");
            }

            log.info("Heist {} foiled by character {}", heistId, byCharacterId);
            return Result.yes(heistId);
        } catch (SQLException e) {
            log.error("Failed to foil heist {}", heistId, e);
            return Result.no("error");
        }
    }

    /** Calls off a job that has not started. */
    public Result abandon(long heistId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_rp_heists SET state = 'abandoned', ended_at = NOW() "
                 + "WHERE id = ? AND state = 'planning'")) {
            ps.setLong(1, heistId);
            return ps.executeUpdate() > 0 ? Result.yes(heistId) : Result.no("job_already_started");
        } catch (SQLException e) {
            log.error("Failed to abandon heist {}", heistId, e);
            return Result.no("error");
        }
    }

    // ─── resolution ─────────────────────────────────────────────────────────

    /**
     * Pays out every job whose timer has run out without being stopped.
     *
     * Driven by a scheduler rather than by the last crew member's client, so a
     * successful heist pays even if everybody involved has disconnected — which
     * is exactly when a crew would otherwise be cheated of it.
     */
    public List<Payout> resolveFinishedHeists() {
        List<Payout> payouts = new ArrayList<>();
        List<long[]> due = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT h.id, h.faction_id, h.target_id, t.payout_min, t.payout_max "
                 + "FROM habnut_rp_heists h JOIN habnut_rp_heist_targets t ON t.id = h.target_id "
                 + "WHERE h.state = 'in_progress' AND h.resolves_at <= NOW()")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    due.add(new long[] {
                        rs.getLong("id"), rs.getInt("faction_id"), rs.getInt("target_id"),
                        rs.getInt("payout_min"), rs.getInt("payout_max") });
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find finished heists", e);
            return payouts;
        }

        for (long[] row : due) {
            settle(row[0], (int) row[1], (int) row[2], (int) row[3], (int) row[4])
                .ifPresent(payouts::add);
        }
        return payouts;
    }

    /**
     * Settles one successful job.
     *
     * The take is rolled once, on the server, and split between the faction and
     * whoever was on the crew. The state is moved to 'succeeded' first and only
     * if that update changes a row, so two schedulers running at once cannot pay
     * the same heist twice.
     */
    private Optional<Payout> settle(long heistId, int factionId, int targetId,
                                    int payoutMin, int payoutMax) {
        int take = payoutMin >= payoutMax
            ? payoutMin
            : ThreadLocalRandom.current().nextInt(payoutMin, payoutMax + 1);

        try (Connection conn = db.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE habnut_rp_heists SET state = 'succeeded', ended_at = NOW(), payout = ? "
                + "WHERE id = ? AND state = 'in_progress'")) {
                ps.setInt(1, take);
                ps.setLong(2, heistId);
                if (ps.executeUpdate() == 0) return Optional.empty();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE habnut_rp_heist_targets SET last_robbed_at = NOW() WHERE id = ?")) {
                ps.setInt(1, targetId);
                ps.executeUpdate();
            }

            List<Long> crew = crewOf(conn, heistId);
            int factionShare = (int) Math.round(take * FACTION_SHARE);
            int perMember = crew.isEmpty() ? 0 : (take - factionShare) / crew.size();

            treasury.credit(factionId, factionShare, RpTreasuryService.Kind.HEIST,
                "Takings from a job", null);

            List<long[]> shares = new ArrayList<>();
            for (long characterId : crew) {
                // Paid as cash in hand rather than into a bank account. It is
                // what a robbery produces, it gives the crew a reason to get
                // somewhere safe before banking it, and it is the money police
                // can take off them if they are caught with it afterwards.
                characters.adjustCash(conn, characterId, perMember);
                recordShare(conn, heistId, characterId, perMember);
                shares.add(new long[] { characterId, perMember });
            }

            log.info("Heist {} paid {} to faction {} and {} each to {} crew",
                heistId, factionShare, factionId, perMember, crew.size());
            return Optional.of(new Payout(heistId, factionId, take, factionShare, shares));
        } catch (SQLException e) {
            log.error("Failed to settle heist {}", heistId, e);
            return Optional.empty();
        }
    }

    private void recordShare(Connection conn, long heistId, long characterId, int share)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "UPDATE habnut_rp_heist_crew SET share = ? WHERE heist_id = ? AND character_id = ?")) {
            ps.setInt(1, share);
            ps.setLong(2, heistId);
            ps.setLong(3, characterId);
            ps.executeUpdate();
        }
    }

    // ─── reading ────────────────────────────────────────────────────────────

    /** Jobs currently open or running, for police and for a faction's own view. */
    public List<Heist> active() {
        List<Heist> heists = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT h.id, h.target_id, t.name AS target_name, h.faction_id, "
                 + "h.leader_character_id, h.state, h.started_at, h.alarm_at, h.resolves_at, "
                 + "h.payout, (SELECT COUNT(*) FROM habnut_rp_heist_crew c "
                 + "           WHERE c.heist_id = h.id) AS crew_size "
                 + "FROM habnut_rp_heists h JOIN habnut_rp_heist_targets t ON t.id = h.target_id "
                 + "WHERE h.state IN ('planning','in_progress') ORDER BY h.created_at")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) heists.add(mapHeist(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to list active heists", e);
        }
        return heists;
    }

    /**
     * Jobs the police should know about: in progress, with the alarm already
     * raised.
     *
     * A heist nobody has noticed yet is not police business, which is the whole
     * reason for the gap between the alarm and the finish.
     */
    public List<Heist> raisedAlarms() {
        List<Heist> heists = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT h.id, h.target_id, t.name AS target_name, h.faction_id, "
                 + "h.leader_character_id, h.state, h.started_at, h.alarm_at, h.resolves_at, "
                 + "h.payout, (SELECT COUNT(*) FROM habnut_rp_heist_crew c "
                 + "           WHERE c.heist_id = h.id) AS crew_size "
                 + "FROM habnut_rp_heists h JOIN habnut_rp_heist_targets t ON t.id = h.target_id "
                 + "WHERE h.state = 'in_progress' AND h.alarm_at <= NOW() "
                 + "ORDER BY h.alarm_at")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) heists.add(mapHeist(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to list raised alarms", e);
        }
        return heists;
    }

    private static Heist mapHeist(ResultSet rs) throws SQLException {
        return new Heist(
            rs.getLong("id"), rs.getInt("target_id"), rs.getString("target_name"),
            rs.getInt("faction_id"), rs.getLong("leader_character_id"),
            rs.getString("state"), String.valueOf(rs.getTimestamp("started_at")),
            String.valueOf(rs.getTimestamp("alarm_at")),
            String.valueOf(rs.getTimestamp("resolves_at")),
            rs.getInt("payout"), rs.getInt("crew_size"));
    }

    // ─── small queries ──────────────────────────────────────────────────────

    private boolean hasOpenHeist(int factionId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT 1 FROM habnut_rp_heists WHERE faction_id = ? "
                 + "AND state IN ('planning','in_progress') LIMIT 1")) {
            ps.setInt(1, factionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("Failed to check open heists for faction {}", factionId, e);
            return true;
        }
    }

    private static String stateOf(Connection conn, long heistId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT state FROM habnut_rp_heists WHERE id = ?")) {
            ps.setLong(1, heistId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static int crewSize(Connection conn, long heistId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT COUNT(*) FROM habnut_rp_heist_crew WHERE heist_id = ?")) {
            ps.setLong(1, heistId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static int maxCrewFor(Connection conn, long heistId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT t.max_crew FROM habnut_rp_heists h "
            + "JOIN habnut_rp_heist_targets t ON t.id = h.target_id WHERE h.id = ?")) {
            ps.setLong(1, heistId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static List<Long> crewOf(Connection conn, long heistId) throws SQLException {
        List<Long> crew = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT character_id FROM habnut_rp_heist_crew WHERE heist_id = ?")) {
            ps.setLong(1, heistId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) crew.add(rs.getLong(1));
            }
        }
        return crew;
    }
}
