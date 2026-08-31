package com.habnut.emulator.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import com.habnut.emulator.auth.UserRepository;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.SessionRegistry;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.protocol.PacketType;
import com.habnut.emulator.room.RoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public final class StaffHandler {

    private static final Logger log = LoggerFactory.getLogger(StaffHandler.class);

    private final StaffCommandDispatcher commandDispatcher;
    private final ModerationService      modService;
    private final UserRepository         userRepo;
    private final RoomManager            roomManager;
    private final SessionRegistry        sessions;
    private final AuditService           audit;
    private final PacketRouter           router;

    public StaffHandler(StaffCommandDispatcher commandDispatcher,
                        ModerationService modService, UserRepository userRepo,
                        RoomManager roomManager, SessionRegistry sessions,
                        AuditService audit, PacketRouter router) {
        this.commandDispatcher = commandDispatcher;
        this.modService        = modService;
        this.userRepo          = userRepo;
        this.roomManager       = roomManager;
        this.sessions          = sessions;
        this.audit             = audit;
        this.router            = router;
    }

    public void register(PacketRouter router) {
        router.register(PacketType.STAFF_COMMAND,         this::handleCommand);
        router.register(PacketType.STAFF_HA,              this::handleHa);
        router.register(PacketType.STAFF_ALERT,           this::handleAlert);
        router.register(PacketType.STAFF_TELEPORT,        this::handleTeleport);
        router.register(PacketType.STAFF_SUMMON,          this::handleSummon);
        router.register(PacketType.STAFF_OVERLAY_REQUEST, this::handleOverlayRequest);
        router.register(PacketType.STAFF_FEATURE_FLAG_SET, this::handleFeatureFlagSet);
        router.register(PacketType.STAFF_SYSTEM_MESSAGE,  this::handleSystemMessage);
    }

    private void handleCommand(WebSocketSession session, JsonNode p) {
        if (!isStaff(session)) { sendError(session, "permission_denied"); return; }
        String command = p.path("command").asText("");
        if (command.isBlank()) { sendError(session, "invalid_payload"); return; }
        commandDispatcher.dispatch(session, command);
    }

    private void handleHa(WebSocketSession session, JsonNode p) {
        if (!isStaff(session)) { sendError(session, "permission_denied"); return; }
        long userId = session.getUserId();
        UserRepository.UserRow user = userRepo.findById(userId);
        String sender  = user != null ? user.username() : String.valueOf(userId);
        String message = p.path("message").asText("");
        if (message.isBlank()) { sendError(session, "invalid_payload"); return; }

        String json = router.buildPacket(PacketType.STAFF_HA_RECEIVED,
            Map.of("sender", sender, "message", message));
        sessions.allStaff(ModerationService.RANK_MOD).forEach(s -> s.send(json));

        audit.log(userId, sender, session.remoteAddress(),
            "staff.ha", null, null, null,
            null, null, Map.of("message", message),
            null, null, null);
    }

    private void handleAlert(WebSocketSession session, JsonNode p) {
        if (!isStaff(session, ModerationService.RANK_ADMIN)) {
            sendError(session, "permission_denied"); return;
        }
        long userId = session.getUserId();
        UserRepository.UserRow user = userRepo.findById(userId);
        String sender  = user != null ? user.username() : String.valueOf(userId);
        String message = p.path("message").asText("");
        if (message.isBlank()) { sendError(session, "invalid_payload"); return; }

        String json = router.buildPacket(PacketType.STAFF_ALERT,
            Map.of("sender", sender, "message", message));
        sessions.all().forEach(s -> s.send(json));

        audit.log(userId, sender, session.remoteAddress(),
            "staff.alert", null, null, null,
            null, null, Map.of("message", message),
            null, null, null);
    }

    private void handleTeleport(WebSocketSession session, JsonNode p) {
        if (!isStaff(session)) { sendError(session, "permission_denied"); return; }
        long targetRoomId = p.path("roomId").asLong(-1);
        if (targetRoomId < 0 || roomManager.get(targetRoomId).isEmpty()) {
            sendError(session, "room_not_found"); return;
        }
        session.send(router.buildPacket(PacketType.STAFF_TELEPORT,
            Map.of("roomId", targetRoomId)));
    }

    private void handleSummon(WebSocketSession session, JsonNode p) {
        if (!isStaff(session)) { sendError(session, "permission_denied"); return; }
        long userId = session.getUserId();
        long targetId = p.path("targetId").asLong(-1);
        UserRepository.UserRow issuer = userRepo.findById(userId);
        String issuerName = issuer != null ? issuer.username() : String.valueOf(userId);

        sessions.byUserId(targetId).ifPresent(ts ->
            ts.send(router.buildPacket(PacketType.STAFF_SUMMONED,
                Map.of("byId", userId, "byName", issuerName))));
    }

    private void handleOverlayRequest(WebSocketSession session, JsonNode p) {
        if (!isStaff(session)) { sendError(session, "permission_denied"); return; }
        try {
            Map<String, Object> overlayData = buildOverlayData();
            session.send(router.buildPacket(PacketType.STAFF_OVERLAY_DATA, overlayData));
        } catch (Exception e) {
            log.error("Overlay data build error", e);
            sendError(session, "server_error");
        }
    }

    private void handleFeatureFlagSet(WebSocketSession session, JsonNode p) {
        if (!isStaff(session, ModerationService.RANK_ADMIN)) {
            sendError(session, "permission_denied"); return;
        }
        String flagName  = p.path("flag").asText("");
        boolean enabled  = p.path("enabled").asBoolean(false);
        long userId      = session.getUserId();

        if (flagName.isBlank()) { sendError(session, "invalid_payload"); return; }

        audit.log(userId, "", session.remoteAddress(),
            "staff.feature_flag", "feature_flag", flagName, flagName,
            null, null, Map.of("enabled", enabled),
            null, null, "system");

        sessions.all().forEach(s -> s.send(router.buildPacket("system.feature_flag",
            Map.of("flag", flagName, "enabled", enabled))));
    }

    private void handleSystemMessage(WebSocketSession session, JsonNode p) {
        if (!isStaff(session, ModerationService.RANK_ADMIN)) {
            sendError(session, "permission_denied"); return;
        }
        String message = p.path("message").asText("");
        if (message.isBlank()) { sendError(session, "invalid_payload"); return; }

        long userId = session.getUserId();
        audit.log(userId, "", session.remoteAddress(),
            "staff.system_message", null, null, null,
            null, null, Map.of("message", message),
            null, null, "system");

        String json = router.buildPacket(PacketType.STAFF_ALERT,
            Map.of("sender", "System", "message", message, "type", "system"));
        sessions.all().forEach(s -> s.send(json));
    }

    private Map<String, Object> buildOverlayData() {
        Map<String, Object> data = new HashMap<>();
        data.put("connectedUsers",     sessions.authenticatedCount());
        data.put("totalConnections",   sessions.connectedCount());
        data.put("loadedRooms",        roomManager.getLoadedCount());
        data.put("onlineStaff",        sessions.allStaff(ModerationService.RANK_MOD).size());
        return data;
    }

    private boolean isStaff(WebSocketSession session) {
        return isStaff(session, ModerationService.RANK_MOD);
    }

    private boolean isStaff(WebSocketSession session, int minRank) {
        Long uid = session.getUserId();
        if (uid == null) return false;
        UserRepository.UserRow u = userRepo.findById(uid);
        return u != null && u.rank() >= minRank;
    }

    private void sendError(WebSocketSession session, String reason) {
        session.send(router.buildPacket("staff.error", Map.of("reason", reason)));
    }
}
