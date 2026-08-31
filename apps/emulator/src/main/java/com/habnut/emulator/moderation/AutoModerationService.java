package com.habnut.emulator.moderation;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Mutes raised by the content policy, and the review that follows.
 *
 * An automatic judgement is a guess. It has to act immediately — a threat left
 * up while somebody waits for a moderator has already done its damage — but it
 * must not be the last word. So a trip mutes the player and raises a case, and
 * the case stays open until a staff member reads what was actually said and
 * either agrees or lifts it.
 *
 * The mute also carries a fallback expiry. A hotel with nobody on duty overnight
 * should not silence somebody indefinitely because a rule misfired at 3am.
 */
public final class AutoModerationService {

    private static final Logger log = LoggerFactory.getLogger(AutoModerationService.class);

    /**
     * How often somebody muted by the policy may ask for a person to look.
     *
     * Long enough that it cannot become a second way of shouting at the room,
     * short enough that a player who was wrongly muted is not left with no way
     * of saying so.
     */
    public static final Duration HELP_REQUEST_INTERVAL = Duration.ofMinutes(15);

    /** An open case: a player muted by the policy, waiting to be reviewed. */
    public record Case(
        long id,
        long userId,
        Long muteId,
        String category,
        String message,
        Instant createdAt,
        Instant muteExpiresAt
    ) {}

    /** What a player is told when the policy stops them. */
    public record MuteNotice(long caseId, String category, Instant expiresAt, boolean canAskForHelp) {}

    private final DatabaseManager db;

    public AutoModerationService(DatabaseManager db) {
        this.db = db;
    }

    // ─── raising a case ─────────────────────────────────────────────────────

    /**
     * Mutes a player and opens a case for review.
     *
     * The mute and the case are written together: a mute nobody can explain, or
     * a case with nothing enforcing it, would each be worse than neither.
     */
    public Optional<MuteNotice> mute(long userId, ContentPolicy.Verdict verdict,
                                     String message, Long roomId) {
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(verdict.muteMinutes()));

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long muteId = insertMute(conn, userId, verdict, expiresAt);
                long caseId = insertCase(conn, userId, muteId, verdict, message, roomId);
                conn.commit();

                log.info("Auto-muted user {} for {} until {} (case {})",
                    userId, verdict.category(), expiresAt, caseId);
                return Optional.of(new MuteNotice(caseId, verdict.category(), expiresAt, true));
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("Failed to auto-mute user {}", userId, e);
            return Optional.empty();
        }
    }

    /** Records a rule that only wanted a look, without silencing anybody. */
    public void flag(long userId, ContentPolicy.Verdict verdict, String message, Long roomId) {
        try (Connection conn = db.getConnection()) {
            insertCase(conn, userId, null, verdict, message, roomId);
        } catch (SQLException e) {
            log.error("Failed to record a flagged message from user {}", userId, e);
        }
    }

    private long insertMute(Connection conn, long userId, ContentPolicy.Verdict verdict,
                            Instant expiresAt) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO habnut_mutes (user_id, muted_by_id, reason, expires_at, source) "
            + "VALUES (?, NULL, ?, ?, 'automatic')", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, "Automatic: " + verdict.rule().label() + " (awaiting review)");
            ps.setTimestamp(3, Timestamp.from(expiresAt));
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No key for mute");
                return keys.getLong(1);
            }
        }
    }

    private long insertCase(Connection conn, long userId, Long muteId,
                            ContentPolicy.Verdict verdict, String message,
                            Long roomId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO habnut_auto_mutes (user_id, mute_id, rule_id, category, message, room_id) "
            + "VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            if (muteId == null) ps.setNull(2, java.sql.Types.INTEGER); else ps.setLong(2, muteId);
            ps.setLong(3, verdict.rule().id());
            ps.setString(4, verdict.category());
            ps.setString(5, message);
            if (roomId == null) ps.setNull(6, java.sql.Types.INTEGER); else ps.setLong(6, roomId);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No key for case");
                return keys.getLong(1);
            }
        }
    }

    // ─── the player's side ──────────────────────────────────────────────────

    /** The open automatic case against a player, if there is one. */
    public Optional<Case> openCaseFor(long userId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT c.id, c.user_id, c.mute_id, c.category, c.message, c.created_at, "
                 + "m.expires_at FROM habnut_auto_mutes c "
                 + "LEFT JOIN habnut_mutes m ON m.id = c.mute_id "
                 + "WHERE c.user_id = ? AND c.status = 'pending_review' "
                 + "ORDER BY c.created_at DESC LIMIT 1")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Timestamp expires = rs.getTimestamp("expires_at");
                return Optional.of(new Case(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getObject("mute_id") == null ? null : rs.getLong("mute_id"),
                    rs.getString("category"),
                    rs.getString("message"),
                    rs.getTimestamp("created_at").toInstant(),
                    expires == null ? null : expires.toInstant()));
            }
        } catch (SQLException e) {
            log.error("Failed to read the open case for user {}", userId, e);
            return Optional.empty();
        }
    }

    /** How long a player must wait before asking for help again, if at all. */
    public Duration timeUntilHelpAllowed(long userId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT created_at FROM habnut_mute_help_requests "
                 + "WHERE user_id = ? ORDER BY created_at DESC LIMIT 1")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Duration.ZERO;

                Duration since = Duration.between(rs.getTimestamp("created_at").toInstant(), Instant.now());
                Duration remaining = HELP_REQUEST_INTERVAL.minus(since);
                return remaining.isNegative() ? Duration.ZERO : remaining;
            }
        } catch (SQLException e) {
            log.error("Failed to check the help interval for user {}", userId, e);
            // A database problem should not be the reason somebody cannot ask
            // for help, so the benefit of the doubt goes to the player.
            return Duration.ZERO;
        }
    }

    /** Why a help request was refused, when it was. */
    public enum HelpRefusal { NOT_AUTOMATICALLY_MUTED, TOO_SOON, FAILED }

    /** The result of asking for a person to look at an automatic mute. */
    public sealed interface HelpResult {
        record Sent(long requestId) implements HelpResult {}
        record Refused(HelpRefusal reason, Duration retryAfter) implements HelpResult {}
    }

    /**
     * Passes a muted player's message to the staff queue.
     *
     * Only a player the policy muted may do this. Somebody a moderator muted has
     * already had a person look at them, and can appeal through the usual route.
     */
    public HelpResult requestHelp(long userId, String message) {
        Optional<Case> open = openCaseFor(userId);
        if (open.isEmpty()) {
            return new HelpResult.Refused(HelpRefusal.NOT_AUTOMATICALLY_MUTED, Duration.ZERO);
        }

        Duration wait = timeUntilHelpAllowed(userId);
        if (!wait.isZero()) {
            return new HelpResult.Refused(HelpRefusal.TOO_SOON, wait);
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_mute_help_requests (auto_mute_id, user_id, message) "
                 + "VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, open.get().id());
            ps.setLong(2, userId);
            ps.setString(3, message == null || message.isBlank()
                ? "No message given." : message.substring(0, Math.min(message.length(), 512)));
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next()
                    ? new HelpResult.Sent(keys.getLong(1))
                    : new HelpResult.Refused(HelpRefusal.FAILED, Duration.ZERO);
            }
        } catch (SQLException e) {
            log.error("Failed to record a help request from user {}", userId, e);
            return new HelpResult.Refused(HelpRefusal.FAILED, Duration.ZERO);
        }
    }

    // ─── the staff side ─────────────────────────────────────────────────────

    /**
     * Settles a case.
     *
     * Overturning it lifts the mute as well, because a case decided in the
     * player's favour that leaves them silenced has not actually been decided.
     */
    public boolean review(long caseId, long staffId, boolean upheld, String notes) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Long muteId = null;
                try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT mute_id FROM habnut_auto_mutes WHERE id = ? AND status = 'pending_review'")) {
                    ps.setLong(1, caseId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        if (rs.getObject("mute_id") != null) muteId = rs.getLong("mute_id");
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE habnut_auto_mutes SET status = ?, reviewed_by_id = ?, "
                    + "reviewed_at = NOW(), review_notes = ? WHERE id = ?")) {
                    ps.setString(1, upheld ? "upheld" : "overturned");
                    ps.setLong(2, staffId);
                    ps.setString(3, notes);
                    ps.setLong(4, caseId);
                    ps.executeUpdate();
                }

                if (!upheld && muteId != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE habnut_mutes SET lifted_at = NOW(), lifted_by_id = ? "
                        + "WHERE id = ? AND lifted_at IS NULL")) {
                        ps.setLong(1, staffId);
                        ps.setLong(2, muteId);
                        ps.executeUpdate();
                    }
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("Failed to review case {}", caseId, e);
            return false;
        }
    }

    /** How many cases are waiting, for the staff overlay to show. */
    public int pendingCaseCount() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM habnut_auto_mutes WHERE status = 'pending_review'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            log.error("Failed to count pending cases", e);
            return 0;
        }
    }
}
