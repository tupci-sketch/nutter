package com.habnut.emulator.moderation;

import com.habnut.emulator.auth.UserRepository;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.SessionRegistry;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.protocol.PacketType;
import com.habnut.emulator.room.Room;
import com.habnut.emulator.room.RoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Map;

public final class StaffCommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(StaffCommandDispatcher.class);

    private final ModerationService modService;
    private final UserRepository    userRepo;
    private final RoomManager       roomManager;
    private final SessionRegistry   sessions;
    private final AuditService      audit;
    private final PacketRouter      router;

    public StaffCommandDispatcher(ModerationService modService, UserRepository userRepo,
                                   RoomManager roomManager, SessionRegistry sessions,
                                   AuditService audit, PacketRouter router) {
        this.modService  = modService;
        this.userRepo    = userRepo;
        this.roomManager = roomManager;
        this.sessions    = sessions;
        this.audit       = audit;
        this.router      = router;
    }

    public void dispatch(WebSocketSession issuer, String rawCommand) {
        if (!rawCommand.startsWith(":")) return;
        String[] parts = rawCommand.substring(1).split("\\s+", 3);
        String cmd  = parts[0].toLowerCase();
        String arg1 = parts.length > 1 ? parts[1] : "";
        String arg2 = parts.length > 2 ? parts[2] : "";

        long issuerId = issuer.getUserId();
        UserRepository.UserRow issuerUser = userRepo.findById(issuerId);
        if (issuerUser == null || issuerUser.rank() < ModerationService.RANK_MOD) {
            sendFeedback(issuer, "Unknown command."); return;
        }
        int issuerRank = issuerUser.rank();

        switch (cmd) {
            case "about"     -> cmdAbout(issuer);
            case "who"       -> cmdWho(issuer);
            case "alert", "ha" -> cmdAlert(issuer, issuerUser.username(), rawCommand.substring(rawCommand.indexOf(' ') + 1));
            case "teleport"  -> cmdTeleport(issuer, arg1, arg2);
            case "summon"    -> cmdSummon(issuer, issuerUser, arg1);
            case "goto"      -> cmdGoto(issuer, issuerUser, arg1);
            case "kick"      -> cmdKick(issuer, issuerId, arg1, arg2);
            case "mute"      -> cmdMute(issuer, issuerId, arg1, arg2);
            case "ban"       -> cmdBan(issuer, issuerId, issuerRank, arg1, arg2);
            case "unban"     -> cmdUnban(issuer, issuerId, arg1);
            case "unmute"    -> cmdUnmute(issuer, issuerId, arg1);
            case "info"      -> cmdInfo(issuer, arg1);
            case "sa"        -> cmdSendAlert(issuer, issuerUser.username(), arg1 + " " + arg2);
            case "roomalert" -> cmdRoomAlert(issuer, issuerUser, arg1 + " " + arg2);
            case "filter"    -> cmdFilter(issuer, issuerRank, arg1, arg2);
            case "rooms"     -> cmdRooms(issuer);
            case "version"   -> cmdVersion(issuer);
            case "shutdown"  -> cmdShutdown(issuer, issuerRank);
            default          -> sendFeedback(issuer, "Unknown command: " + cmd);
        }
    }

    private void cmdAbout(WebSocketSession s) {
        sendFeedback(s, "Habnut Emulator — platform build.");
    }

    private void cmdWho(WebSocketSession s) {
        long count = sessions.allStaff(ModerationService.RANK_MOD).size();
        sendFeedback(s, count + " staff member(s) currently online.");
    }

    private void cmdAlert(WebSocketSession s, String sender, String text) {
        String json = router.buildPacket(PacketType.STAFF_HA_RECEIVED,
            Map.of("sender", sender, "message", text));
        sessions.allStaff(ModerationService.RANK_MOD).forEach(st -> st.send(json));
    }

    private void cmdSendAlert(WebSocketSession s, String sender, String text) {
        String json = router.buildPacket(PacketType.STAFF_ALERT,
            Map.of("sender", sender, "message", text.trim()));
        sessions.all().forEach(st -> st.send(json));
        sendFeedback(s, "Alert sent to all users.");
    }

    private void cmdRoomAlert(WebSocketSession s, UserRepository.UserRow issuer, String text) {
        Room currentRoom = currentRoom(s);
        if (currentRoom == null) { sendFeedback(s, "You are not in a room."); return; }
        String json = router.buildPacket(PacketType.STAFF_ALERT,
            Map.of("sender", issuer.username(), "message", text.trim()));
        currentRoom.getAllUserIds().forEach(uid ->
            sessions.byUserId(uid).ifPresent(st -> st.send(json)));
    }

    private void cmdTeleport(WebSocketSession s, String roomIdStr, String coords) {
        long roomId = parseLong(roomIdStr, -1);
        if (roomId < 0 || roomManager.get(roomId).isEmpty()) {
            sendFeedback(s, "Room not found: " + roomIdStr); return;
        }
        s.send(router.buildPacket(PacketType.STAFF_TELEPORT,
            Map.of("roomId", roomId)));
        sendFeedback(s, "Teleporting to room " + roomId + ".");
    }

    private void cmdSummon(WebSocketSession s, UserRepository.UserRow issuer, String target) {
        UserRepository.UserRow targetUser = findUser(target);
        if (targetUser == null) { sendFeedback(s, "User not found: " + target); return; }
        sessions.byUserId(targetUser.id()).ifPresent(ts ->
            ts.send(router.buildPacket(PacketType.STAFF_SUMMONED,
                Map.of("byId", issuer.id(), "byName", issuer.username()))));
        sendFeedback(s, "Summoned " + targetUser.username() + ".");
    }

    private void cmdGoto(WebSocketSession s, UserRepository.UserRow issuer, String target) {
        UserRepository.UserRow targetUser = findUser(target);
        if (targetUser == null) { sendFeedback(s, "User not found: " + target); return; }
        sendFeedback(s, "Navigating to " + targetUser.username() + "'s room.");
    }

    private void cmdKick(WebSocketSession s, long issuerId, String target, String reason) {
        UserRepository.UserRow u = findUser(target);
        if (u == null) { sendFeedback(s, "User not found: " + target); return; }
        sessions.byUserId(u.id()).ifPresent(ts ->
            ts.send(router.buildPacket(PacketType.MOD_USER_KICKED,
                Map.of("reason", reason.isBlank() ? "Kicked by staff" : reason))));
        sendFeedback(s, "Kicked " + u.username() + ".");
        audit.log(issuerId, "", "", "user.kick", "user", String.valueOf(u.id()),
            u.username(), null, null, Map.of("reason", reason), null, null, null);
    }

    private void cmdMute(WebSocketSession s, long issuerId, String target, String minutes) {
        UserRepository.UserRow u = findUser(target);
        if (u == null) { sendFeedback(s, "User not found: " + target); return; }
        int mins = parseInt(minutes, 60);
        try {
            modService.mute(issuerId, u.id(), "Staff command mute", null, mins);
            sessions.byUserId(u.id()).ifPresent(ts ->
                ts.send(router.buildPacket(PacketType.MOD_USER_MUTED,
                    Map.of("reason", "Muted by staff", "minutes", mins))));
            sendFeedback(s, "Muted " + u.username() + " for " + mins + " minutes.");
        } catch (SQLException e) { sendFeedback(s, "Error: " + e.getMessage()); }
    }

    private void cmdUnmute(WebSocketSession s, long issuerId, String target) {
        UserRepository.UserRow u = findUser(target);
        if (u == null) { sendFeedback(s, "User not found: " + target); return; }
        try {
            boolean ok = modService.unmute(issuerId, u.id(), null);
            sendFeedback(s, ok ? "Unmuted " + u.username() + "." : "No active mute found.");
        } catch (SQLException e) { sendFeedback(s, "Error: " + e.getMessage()); }
    }

    private void cmdBan(WebSocketSession s, long issuerId, int issuerRank,
                         String target, String args) {
        if (issuerRank < ModerationService.RANK_ADMIN) {
            sendFeedback(s, "Insufficient rank to ban."); return;
        }
        UserRepository.UserRow u = findUser(target);
        if (u == null) { sendFeedback(s, "User not found: " + target); return; }
        try {
            modService.ban(issuerId, u.id(), args.isBlank() ? "Banned by staff" : args,
                "standard", null, null, null);
            sessions.byUserId(u.id()).ifPresent(ts -> {
                ts.send(router.buildPacket(PacketType.MOD_USER_BANNED,
                    Map.of("reason", args.isBlank() ? "Banned by staff" : args, "banId", -1)));
                ts.close();
            });
            sendFeedback(s, "Banned " + u.username() + ".");
        } catch (SQLException e) { sendFeedback(s, "Error: " + e.getMessage()); }
    }

    private void cmdUnban(WebSocketSession s, long issuerId, String banIdStr) {
        long banId = parseLong(banIdStr, -1);
        if (banId < 0) { sendFeedback(s, "Usage: :unban <banId>"); return; }
        try {
            boolean ok = modService.unban(issuerId, banId);
            sendFeedback(s, ok ? "Ban " + banId + " lifted." : "Ban not found.");
        } catch (SQLException e) { sendFeedback(s, "Error: " + e.getMessage()); }
    }

    private void cmdInfo(WebSocketSession s, String target) {
        UserRepository.UserRow u = findUser(target);
        if (u == null) { sendFeedback(s, "User not found: " + target); return; }
        try {
            modService.getUserSummary(u.id()).ifPresent(summary ->
                s.send(router.buildPacket(PacketType.MOD_USER_INFO_RESULT, Map.of(
                    "id", summary.id(), "username", summary.username(),
                    "rank", summary.rank(), "currentlyMuted", summary.currentlyMuted(),
                    "openReports", summary.openReports(), "totalBans", summary.totalBans()
                ))));
        } catch (SQLException e) { sendFeedback(s, "Error: " + e.getMessage()); }
    }

    private void cmdFilter(WebSocketSession s, int issuerRank, String action, String word) {
        if (issuerRank < ModerationService.RANK_ADMIN) {
            sendFeedback(s, "Insufficient rank."); return;
        }
        sendFeedback(s, "Word filter management available via DCC.");
    }

    private void cmdRooms(WebSocketSession s) {
        int count = roomManager.getLoadedCount();
        sendFeedback(s, count + " room(s) currently active.");
    }

    private void cmdVersion(WebSocketSession s) {
        sendFeedback(s, "Habnut Emulator — build version from manifest.");
    }

    private void cmdShutdown(WebSocketSession s, int issuerRank) {
        if (issuerRank < ModerationService.RANK_ADMIN) {
            sendFeedback(s, "Insufficient rank."); return;
        }
        sendFeedback(s, "Shutdown must be initiated from the server console.");
    }

    private Room currentRoom(WebSocketSession s) {
        return null;
    }

    private void sendFeedback(WebSocketSession s, String message) {
        s.send(router.buildPacket(PacketType.STAFF_COMMAND_RESPONSE, Map.of("message", message)));
    }

    private UserRepository.UserRow findUser(String nameOrId) {
        try {
            long id = Long.parseLong(nameOrId);
            return userRepo.findById(id);
        } catch (NumberFormatException e) {
            return userRepo.findByUsername(nameOrId);
        }
    }

    private long parseLong(String s, long def) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return def; }
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}
