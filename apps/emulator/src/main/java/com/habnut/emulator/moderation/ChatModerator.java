package com.habnut.emulator.moderation;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * The gate every spoken message passes through.
 *
 * There are three questions to answer before a message reaches a room, and they
 * have to be answered in this order: is this player allowed to speak at all, is
 * what they said harmful, and does the hotel's word list want anything changed.
 * Asking them anywhere else means a muted player still talks, which is the whole
 * point of a mute.
 */
public final class ChatModerator {

    private static final Logger log = LoggerFactory.getLogger(ChatModerator.class);

    /** What the caller should do with a message. */
    public sealed interface Decision {

        /** Say it, in this form — the word filter may have changed it. */
        record Allow(String message) implements Decision {}

        /** Do not say it; the player is muted and this is why. */
        record Muted(String reason, Instant expiresAt, boolean automatic,
                     boolean canAskForHelp) implements Decision {}

        /** Do not say it; the policy stopped it and has just muted the player. */
        record Stopped(String category, AutoModerationService.MuteNotice notice) implements Decision {}
    }

    private final DatabaseManager db;
    private final ContentPolicy policy;
    private final AutoModerationService autoMod;
    private final WordFilter wordFilter;

    public ChatModerator(DatabaseManager db, ContentPolicy policy,
                         AutoModerationService autoMod, WordFilter wordFilter) {
        this.db = db;
        this.policy = policy;
        this.autoMod = autoMod;
        this.wordFilter = wordFilter;
    }

    /**
     * Decides what happens to one message.
     *
     * @param roomId the room it was said in, or null for chat with no room
     */
    public Decision moderate(long userId, String message, Long roomId) {
        Optional<ActiveMute> mute = activeMute(userId);
        if (mute.isPresent()) {
            ActiveMute m = mute.get();
            return new Decision.Muted(
                m.reason(),
                m.expiresAt(),
                m.automatic(),
                // Only somebody the policy muted can ask for a review; anybody a
                // moderator muted has already had a person look at them.
                m.automatic() && autoMod.timeUntilHelpAllowed(userId).isZero());
        }

        Optional<ContentPolicy.Verdict> verdict = policy.check(message);
        if (verdict.isPresent()) {
            ContentPolicy.Verdict v = verdict.get();

            if (v.shouldMute()) {
                return autoMod.mute(userId, v, message, roomId)
                    .<Decision>map(notice -> new Decision.Stopped(v.category(), notice))
                    // If the mute could not be written, the message is still not
                    // said: failing open on this category of content is worse
                    // than a player losing one line of chat.
                    .orElseGet(() -> new Decision.Stopped(v.category(), null));
            }

            // A flagged message goes through, but a case is opened so somebody
            // reads it. These rules are the ones too broad to act on alone.
            autoMod.flag(userId, v, message, roomId);
        }

        WordFilter.FilterResult filtered = wordFilter.apply(message);
        if (filtered.blocked()) {
            return new Decision.Muted("That cannot be said here.", null, false, false);
        }
        return new Decision.Allow(filtered.filtered());
    }

    /** A mute currently in force. */
    private record ActiveMute(String reason, Instant expiresAt, boolean automatic) {}

    /**
     * The mute in force against a player, if any.
     *
     * Read on every message rather than cached, because a moderator lifting a
     * mute has to take effect on the player's next line, not whenever a cache
     * happens to expire.
     */
    private Optional<ActiveMute> activeMute(long userId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT reason, expires_at, source FROM habnut_mutes "
                 + "WHERE user_id = ? AND lifted_at IS NULL AND expires_at > NOW() "
                 + "ORDER BY expires_at DESC LIMIT 1")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();

                Timestamp expires = rs.getTimestamp("expires_at");
                return Optional.of(new ActiveMute(
                    rs.getString("reason"),
                    expires == null ? null : expires.toInstant(),
                    "automatic".equalsIgnoreCase(rs.getString("source"))));
            }
        } catch (SQLException e) {
            // A player is not silenced because the database is unwell.
            log.error("Failed to check the mute on user {}", userId, e);
            return Optional.empty();
        }
    }
}
