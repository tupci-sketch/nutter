package com.habnut.emulator.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import com.habnut.emulator.auth.UserRepository;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.SessionRegistry;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.protocol.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The player's and the staff's side of an automatic mute.
 *
 * A player stopped by the content policy needs two things: to know what
 * happened, and a way to have a person look at it. Without the second, an
 * automatic mute is indistinguishable from the hotel being broken, and the
 * player who was wrongly caught has no recourse but to wait.
 */
public final class AutoModerationHandler {

    private static final Logger log = LoggerFactory.getLogger(AutoModerationHandler.class);

    /** Rank at which a staff member sees automatic-mute alerts. */
    private static final int STAFF_RANK = ModerationService.RANK_MOD;

    private final AutoModerationService autoMod;
    private final UserRepository userRepo;
    private final SessionRegistry sessions;
    private final PacketRouter router;

    public AutoModerationHandler(AutoModerationService autoMod, UserRepository userRepo,
                                 SessionRegistry sessions, PacketRouter router) {
        this.autoMod = autoMod;
        this.userRepo = userRepo;
        this.sessions = sessions;
        this.router = router;
    }

    public void register(PacketRouter router) {
        router.register(PacketType.MOD_AUTO_MUTE_STATE, this::handleState);
        router.register(PacketType.MOD_AUTO_MUTE_HELP, this::handleHelp);
    }

    /** Tells a player where their automatic mute stands. */
    private void handleState(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;

        long userId = session.getUserId();
        Optional<AutoModerationService.Case> open = autoMod.openCaseFor(userId);

        Map<String, Object> body = new HashMap<>();
        body.put("muted", open.isPresent());
        body.put("automatic", open.isPresent());

        if (open.isPresent()) {
            AutoModerationService.Case c = open.get();
            Duration wait = autoMod.timeUntilHelpAllowed(userId);

            body.put("category", c.category());
            body.put("canAskForHelp", wait.isZero());
            body.put("secondsUntilHelpAllowed", wait.toSeconds());
            if (c.muteExpiresAt() != null) body.put("expiresAt", c.muteExpiresAt().toString());
        } else {
            body.put("canAskForHelp", false);
            body.put("secondsUntilHelpAllowed", 0L);
        }

        session.send(router.buildPacket(PacketType.MOD_AUTO_MUTE_STATE, body));
    }

    /**
     * Passes a muted player's message to the staff queue.
     *
     * The wait between requests is enforced here rather than in the client,
     * because a client is not a place to keep a rule that matters.
     */
    private void handleHelp(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;

        long userId = session.getUserId();
        String message = payload.path("message").asText("").trim();

        AutoModerationService.HelpResult result = autoMod.requestHelp(userId, message);

        if (result instanceof AutoModerationService.HelpResult.Sent) {
            session.send(router.buildPacket(PacketType.MOD_AUTO_MUTE_HELP_ACK, Map.of(
                "sent", true,
                "secondsUntilHelpAllowed", AutoModerationService.HELP_REQUEST_INTERVAL.toSeconds(),
                "message", "Sent. A staff member will read what you said and get back to you.")));
            alertStaff(userId);
            return;
        }

        AutoModerationService.HelpResult.Refused refused =
            (AutoModerationService.HelpResult.Refused) result;

        session.send(router.buildPacket(PacketType.MOD_AUTO_MUTE_HELP_ACK, Map.of(
            "sent", false,
            "secondsUntilHelpAllowed", refused.retryAfter().toSeconds(),
            "message", explain(refused))));
    }

    private static String explain(AutoModerationService.HelpResult.Refused refused) {
        return switch (refused.reason()) {
            case NOT_AUTOMATICALLY_MUTED ->
                "You have no automatic mute to review. If a staff member muted you, "
                + "you can appeal on the website.";
            case TOO_SOON -> {
                long minutes = Math.max(1, refused.retryAfter().toMinutes());
                yield "You have already asked. You can ask again in " + minutes
                    + (minutes == 1 ? " minute." : " minutes.");
            }
            case FAILED -> "That could not be sent. Please try again shortly.";
        };
    }

    /** Lets on-duty staff know somebody is waiting. */
    private void alertStaff(long userId) {
        UserRepository.UserRow user = userRepo.findById(userId);
        String username = user == null ? "A player" : user.username();

        String packet = router.buildPacket(PacketType.MOD_AUTO_MUTE_ALERT, Map.of(
            "userId", userId,
            "username", username,
            "pendingCases", autoMod.pendingCaseCount()));

        sessions.all().stream()
            .filter(WebSocketSession::isAuthenticated)
            .filter(this::isStaff)
            .forEach(s -> s.send(packet));

        log.info("{} asked for a review of their automatic mute", username);
    }

    private boolean isStaff(WebSocketSession session) {
        Long uid = session.getUserId();
        if (uid == null) return false;
        UserRepository.UserRow user = userRepo.findById(uid);
        return user != null && user.rank() >= STAFF_RANK;
    }
}
