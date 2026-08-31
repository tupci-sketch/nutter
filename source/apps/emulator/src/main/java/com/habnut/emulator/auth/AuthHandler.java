package com.habnut.emulator.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.habnut.emulator.metrics.MetricsRegistry;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.SessionRegistry;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.protocol.ErrorCode;
import com.habnut.emulator.protocol.PacketType;
import com.habnut.emulator.redis.RedisManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

public final class AuthHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthHandler.class);

    private final SessionTicketService tickets;
    private final UserRepository users;
    private final BanService bans;
    private final MachineIdService machineIds;
    private final SessionRegistry sessions;
    private final PacketRouter router;
    private final MetricsRegistry metrics;
    private final RedisManager redis;

    public AuthHandler(SessionTicketService tickets, UserRepository users,
                       BanService bans, MachineIdService machineIds,
                       SessionRegistry sessions, PacketRouter router,
                       MetricsRegistry metrics, RedisManager redis) {
        this.tickets    = tickets;
        this.users      = users;
        this.bans       = bans;
        this.machineIds = machineIds;
        this.sessions   = sessions;
        this.router     = router;
        this.metrics    = metrics;
        this.redis      = redis;
    }

    public void register(PacketRouter router) {
        router.register(PacketType.AUTH_LOGIN,        this::handleLogin);
        router.register(PacketType.AUTH_LOGOUT,       this::handleLogout);
        router.register(PacketType.AUTH_WORLD_SWITCH, this::handleWorldSwitch);
    }

    private void handleLogin(WebSocketSession session, JsonNode payload) {
        if (session.isAuthenticated()) {
            sendError(session, ErrorCode.AUTH_ALREADY_LOGGED_IN, "Already authenticated");
            return;
        }

        String ticket    = payload.path("ticket").asText(null);
        String machineId = payload.path("machineId").asText(null);
        String ip        = session.remoteAddress();

        if (ticket == null || ticket.isBlank()) {
            sendError(session, ErrorCode.AUTH_INVALID_TICKET, "Missing ticket");
            metrics.incrementAuthFailure();
            return;
        }

        if (machineId != null && machineIds.isBanned(machineId)) {
            sendError(session, ErrorCode.AUTH_BANNED, "Device banned");
            metrics.incrementAuthFailure();
            return;
        }

        SessionTicketService.TicketClaim claim = tickets.consume(ticket);
        if (claim == null) {
            sendError(session, ErrorCode.AUTH_INVALID_TICKET, "Invalid or expired ticket");
            metrics.incrementAuthFailure();
            return;
        }

        if (bans.isBanned(claim.userId())) {
            BanService.ActiveBan ban = bans.getActiveBan(claim.userId());
            String expires = ban != null ? ban.expiresAt() : null;
            String reason  = ban != null ? ban.reason() : "Banned";
            sendBanned(session, reason, expires);
            metrics.incrementAuthFailure();
            return;
        }

        UserRepository.UserRow user = users.findById(claim.userId());
        if (user == null) {
            sendError(session, ErrorCode.AUTH_INVALID_TICKET, "User not found");
            metrics.incrementAuthFailure();
            return;
        }

        // Kick existing session for this user
        sessions.byUserId(user.id()).ifPresent(existing -> {
            existing.closeAfterSend(router.buildPacket(PacketType.AUTH_KICKED,
                Map.of("reason", "logged_in_elsewhere")));
        });

        session.authenticate(user.id(), claim.worldId());
        sessions.onAuthenticated(session, user.id(), user.rank());

        redis.sadd(RedisManager.KEY_ONLINE, String.valueOf(user.id()));
        users.updateLastLogin(user.id(), ip);
        if (machineId != null) machineIds.record(user.id(), machineId, ip);

        MDC.put("userId", String.valueOf(user.id()));

        String lastLoginStr = user.lastLogin() != null ? user.lastLogin().toString() : null;

        session.send(router.buildPacket(PacketType.AUTH_LOGIN_SUCCESS, Map.of(
            "userId",      user.id(),
            "username",    user.username(),
            "figureString",user.figureString(),
            "rank",        user.rank(),
            "credits",     user.credits(),
            "diamonds",    user.diamonds(),
            "nutPoints",   user.nutPoints(),
            "lastLogin",   lastLoginStr != null ? lastLoginStr : "",
            "worldId",     claim.worldId()
        )));

        metrics.incrementAuthSuccess();
        log.info("User {} authenticated for world {}", user.username(), claim.worldId());
    }

    private void handleLogout(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) {
            session.close();
            return;
        }
        Long userId = session.getUserId();
        if (userId != null) {
            redis.srem(RedisManager.KEY_ONLINE, String.valueOf(userId));
        }
        session.deauthenticate();
        session.close();
        log.debug("User {} logged out", userId);
    }

    private void handleWorldSwitch(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) {
            sendError(session, ErrorCode.AUTH_NOT_AUTHENTICATED, "Not authenticated");
            return;
        }
        String targetWorld = payload.path("worldId").asText(null);
        if (targetWorld == null || targetWorld.isBlank()) {
            sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Missing worldId");
            return;
        }

        long userId = session.getUserId();
        String ticket = tickets.issue(userId, targetWorld);
        session.send(router.buildPacket(PacketType.AUTH_WORLD_SWITCHED,
            Map.of("worldId", targetWorld, "ticket", ticket)));
        log.debug("User {} switching to world {}", userId, targetWorld);
    }

    private void sendError(WebSocketSession session, String code, String message) {
        session.send(router.buildPacket(PacketType.AUTH_LOGIN_ERROR,
            Map.of("code", code, "message", message)));
    }

    private void sendBanned(WebSocketSession session, String reason, String expiresAt) {
        session.closeAfterSend(router.buildPacket(PacketType.AUTH_LOGIN_ERROR,
            Map.of("code", ErrorCode.AUTH_BANNED, "reason", reason,
                   "expiresAt", expiresAt != null ? expiresAt : "")));
    }
}
