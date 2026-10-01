package com.habnut.emulator.moderation;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.*;
import java.util.stream.Collectors;

public final class ModerationHandler {

    private static final Logger log = LoggerFactory.getLogger(ModerationHandler.class);

    private final ModerationService modService;
    private final ChatLogService    chatLogs;
    private final UserRepository    userRepo;
    private final RoomManager       roomManager;
    private final SessionRegistry   sessions;
    private final AuditService      audit;
    private final PacketRouter      router;

    public ModerationHandler(ModerationService modService, ChatLogService chatLogs,
                             UserRepository userRepo, RoomManager roomManager,
                             SessionRegistry sessions, AuditService audit, PacketRouter router) {
        this.modService  = modService;
        this.chatLogs    = chatLogs;
        this.userRepo    = userRepo;
        this.roomManager = roomManager;
        this.sessions    = sessions;
        this.audit       = audit;
        this.router      = router;
    }

    public void register(PacketRouter router) {
        router.register(PacketType.MOD_REPORT_CREATE,  this::handleReportCreate);
        router.register(PacketType.MOD_REPORT_LIST,    this::handleReportList);
        router.register(PacketType.MOD_REPORT_CLAIM,   this::handleReportClaim);
        router.register(PacketType.MOD_REPORT_RESOLVE, this::handleReportResolve);
        router.register(PacketType.MOD_USER_MUTE,      this::handleMute);
        router.register(PacketType.MOD_USER_KICK,      this::handleKick);
        router.register(PacketType.MOD_USER_BAN,       this::handleBan);
        router.register(PacketType.MOD_USER_UNBAN,     this::handleUnban);
        router.register(PacketType.MOD_USER_WARN,      this::handleWarn);
        router.register(PacketType.MOD_CHAT_LOGS,      this::handleChatLogs);
        router.register(PacketType.MOD_USER_INFO,      this::handleUserInfo);
        router.register(PacketType.MOD_CALL_FOR_HELP,  this::handleCallForHelp);
    }

    private void handleReportCreate(WebSocketSession session, JsonNode p) {
        long userId    = session.getUserId();
        long targetId  = p.path("targetId").asLong(-1);
        String category   = p.path("category").asText("");
        String description = p.path("description").asText("");
        long roomId    = p.path("roomId").asLong(-1);
        String roomName = p.path("roomName").asText("Unknown");

        if (targetId < 0 || category.isBlank() || description.isBlank()) {
            sendError(session, "invalid_payload"); return;
        }
        try {
            long reportId = modService.createReport(userId, targetId, category, description,
                roomId > 0 ? roomId : null, roomName);
            session.send(router.buildPacket(PacketType.MOD_REPORT_CREATED,
                Map.of("reportId", reportId)));
            alertOnlineStaff(reportId, targetId);
        } catch (SQLException e) {
            log.error("Report create error", e);
            sendError(session, "server_error");
        }
    }

    private void handleReportList(WebSocketSession session, JsonNode p) {
        if (!isStaff(session)) { sendError(session, "permission_denied"); return; }
        try {
            List<ModerationService.Report> reports = modService.listOpenReports(20);
            session.send(router.buildPacket(PacketType.MOD_REPORT_LIST,
                Map.of("reports", reports.stream().map(this::reportToMap).collect(Collectors.toList()))));
        } catch (SQLException e) {
            log.error("Report list error", e);
            sendError(session, "server_error");
        }
    }

    private void handleReportClaim(WebSocketSession session, JsonNode p) {
        if (!isStaff(session)) { sendError(session, "permission_denied"); return; }
        long reportId = p.path("reportId").asLong(-1);
        try {
            boolean ok = modService.claimReport(reportId, session.getUserId());
            if (!ok) sendError(session, "already_claimed");
        } catch (SQLException e) {
            log.error("Report claim error", e);
            sendError(session, "server_error");
        }
    }

    private void handleReportResolve(WebSocketSession session, JsonNode p) {
        if (!isStaff(session)) { sendError(session, "permission_denied"); return; }
        long reportId  = p.path("reportId").asLong(-1);
        String resolution = p.path("resolution").asText("");
        String outcome    = p.path("outcome").asText("resolved");
        try {
            modService.resolveReport(reportId, session.getUserId(), resolution, outcome);
        } catch (SQLException e) {
            log.error("Report resolve error", e);
            sendError(session, "server_error");
        }
    }

    private void handleMute(WebSocketSession session, JsonNode p) {
        if (!isStaff(session)) { sendError(session, "permission_denied"); return; }
        long targetId  = p.path("targetId").asLong(-1);
        String reason  = p.path("reason").asText("No reason given");
        int minutes    = p.path("minutes").asInt(60);
        long roomId    = p.path("roomId").asLong(-1);

        try {
            modService.mute(session.getUserId(), targetId, reason,
                roomId > 0 ? roomId : null, Math.min(minutes, 60 * 24 * 30));
            sessions.byUserId(targetId).ifPresent(s ->
                s.send(router.buildPacket(PacketType.MOD_USER_MUTED,
                    Map.of("reason", reason, "minutes", minutes))));
            audit.log(session.getUserId(), "", session.remoteAddress(),
                "user.mute", "user", String.valueOf(targetId), "",
                null, null, Map.of("minutes", minutes, "reason", reason),
                null, roomId > 0 ? Long.valueOf(roomId) : null, null);
        } catch (SQLException e) {
            log.error("Mute error", e);
            sendError(session, "server_error");
        }
    }

    private void handleKick(WebSocketSession session, JsonNode p) {
        if (!isStaff(session)) { sendError(session, "permission_denied"); return; }
        long targetId = p.path("targetId").asLong(-1);
        String reason = p.path("reason").asText("Kicked by staff");
        long roomId   = p.path("roomId").asLong(-1);

        Optional<Room> roomOpt = roomId > 0 ? roomManager.get(roomId) : Optional.empty();
        if (roomOpt.isEmpty()) { sendError(session, "room_not_found"); return; }

        sessions.byUserId(targetId).ifPresent(s ->
            s.send(router.buildPacket(PacketType.MOD_USER_KICKED,
                Map.of("reason", reason))));
        audit.log(session.getUserId(), "", session.remoteAddress(),
            "user.kick", "user", String.valueOf(targetId), "",
            null, null, Map.of("roomId", roomId, "reason", reason),
            null, Long.valueOf(roomId), null);
    }

    private void handleBan(WebSocketSession session, JsonNode p) {
        if (!isStaff(session)) { sendError(session, "permission_denied"); return; }
        long targetId   = p.path("targetId").asLong(-1);
        String reason   = p.path("reason").asText("No reason given");
        String banType  = p.path("banType").asText("standard");
        int hours       = p.path("hours").asInt(0);

        try {
            long banId = modService.ban(session.getUserId(), targetId, reason, banType,
                null, null, hours > 0 ? hours : null);
            sessions.byUserId(targetId).ifPresent(s -> {
                s.send(router.buildPacket(PacketType.MOD_USER_BANNED,
                    Map.of("reason", reason, "banId", banId)));
                s.close();
            });
            audit.log(session.getUserId(), "", session.remoteAddress(),
                "user.ban", "user", String.valueOf(targetId), "",
                null, null, Map.of("banId", banId, "hours", hours),
                null, null, null);
        } catch (SQLException e) {
            log.error("Ban error", e);
            sendError(session, "server_error");
        }
    }

    private void handleUnban(WebSocketSession session, JsonNode p) {
        if (!isStaff(session)) { sendError(session, "permission_denied"); return; }
        long banId = p.path("banId").asLong(-1);
        try {
            boolean ok = modService.unban(session.getUserId(), banId);
            if (!ok) sendError(session, "ban_not_found");
        } catch (SQLException e) {
            log.error("Unban error", e);
            sendError(session, "server_error");
        }
    }

    private void handleWarn(WebSocketSession session, JsonNode p) {
        if (!isStaff(session)) { sendError(session, "permission_denied"); return; }
        long targetId = p.path("targetId").asLong(-1);
        String reason = p.path("reason").asText("No reason given");
        try {
            modService.warn(session.getUserId(), targetId, reason);
            sessions.byUserId(targetId).ifPresent(s ->
                s.send(router.buildPacket(PacketType.MOD_USER_WARNED,
                    Map.of("reason", reason))));
        } catch (SQLException e) {
            log.error("Warn error", e);
            sendError(session, "server_error");
        }
    }

    private void handleChatLogs(WebSocketSession session, JsonNode p) {
        if (!isStaff(session)) { sendError(session, "permission_denied"); return; }
        long userId   = p.path("userId").asLong(-1);
        long roomId   = p.path("roomId").asLong(-1);
        String query  = p.path("query").asText("");
        int limit     = Math.min(p.path("limit").asInt(25), 100);

        try {
            List<ChatLogService.ChatEntry> entries;
            if (!query.isBlank()) {
                entries = chatLogs.searchFullText(query, limit);
            } else if (userId > 0) {
                entries = chatLogs.searchByUser(userId, limit);
            } else if (roomId > 0) {
                entries = chatLogs.searchByRoom(roomId, limit);
            } else {
                sendError(session, "invalid_payload"); return;
            }
            session.send(router.buildPacket(PacketType.MOD_CHAT_LOGS_RESULT,
                Map.of("entries", entries.stream().map(this::chatEntryToMap)
                    .collect(Collectors.toList()))));
        } catch (SQLException e) {
            log.error("Chat log search error", e);
            sendError(session, "server_error");
        }
    }

    private void handleUserInfo(WebSocketSession session, JsonNode p) {
        if (!isStaff(session)) { sendError(session, "permission_denied"); return; }
        long targetId = p.path("targetId").asLong(-1);
        try {
            Optional<ModerationService.UserSummary> opt = modService.getUserSummary(targetId);
            if (opt.isEmpty()) { sendError(session, "user_not_found"); return; }
            ModerationService.UserSummary u = opt.get();
            List<ModerationService.BanRecord> bans = modService.getBans(targetId);
            session.send(router.buildPacket(PacketType.MOD_USER_INFO_RESULT, Map.of(
                "id",           u.id(),
                "username",     u.username(),
                "rank",         u.rank(),
                "lastIp",       u.lastIp() != null ? u.lastIp() : "",
                "openReports",  u.openReports(),
                "totalBans",    u.totalBans(),
                "currentlyMuted", u.currentlyMuted(),
                "bans",         bans.stream().map(this::banToMap).collect(Collectors.toList())
            )));
        } catch (SQLException e) {
            log.error("User info error", e);
            sendError(session, "server_error");
        }
    }

    private void handleCallForHelp(WebSocketSession session, JsonNode p) {
        long userId  = session.getUserId();
        String msg   = p.path("message").asText("");
        long roomId  = p.path("roomId").asLong(-1);

        alertOnlineStaff(-1, userId);
        UserRepository.UserRow user = userRepo.findById(userId);
        String username = user != null ? user.username() : String.valueOf(userId);

        String alert = router.buildPacket(PacketType.MOD_REPORT_ALERT,
            Map.of("type", "call_for_help", "userId", userId, "username", username,
                "message", msg, "roomId", roomId));
        sessions.allStaff(ModerationService.RANK_MOD).forEach(s -> s.send(alert));
    }

    private void alertOnlineStaff(long reportId, long targetId) {
        UserRepository.UserRow target = userRepo.findById(targetId);
        String targetName = target != null ? target.username() : String.valueOf(targetId);
        String alert = router.buildPacket(PacketType.MOD_REPORT_ALERT,
            Map.of("reportId", reportId, "targetId", targetId, "targetName", targetName));
        sessions.allStaff(ModerationService.RANK_MOD).forEach(s -> s.send(alert));
    }

    private boolean isStaff(WebSocketSession session) {
        Long uid = session.getUserId();
        if (uid == null) return false;
        UserRepository.UserRow u = userRepo.findById(uid);
        return u != null && u.rank() >= ModerationService.RANK_MOD;
    }

    private Map<String, Object> reportToMap(ModerationService.Report r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",           r.id());
        m.put("reporterId",   r.reporterId());
        m.put("reporterName", r.reporterName());
        m.put("targetId",     r.targetId());
        m.put("targetName",   r.targetName());
        m.put("category",     r.category());
        m.put("description",  r.description());
        m.put("status",       r.status());
        m.put("claimedById",  r.claimedById());
        m.put("createdAt",    r.createdAt());
        return m;
    }

    private Map<String, Object> chatEntryToMap(ChatLogService.ChatEntry e) {
        return Map.of("id", e.id(), "userId", e.userId(), "username", e.username(),
            "roomId", e.roomId(), "roomName", e.roomName(), "message", e.message(),
            "type", e.type(), "timestamp", e.timestamp());
    }

    private Map<String, Object> banToMap(ModerationService.BanRecord b) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",        b.id());
        m.put("reason",    b.reason());
        m.put("banType",   b.banType());
        m.put("expiresAt", b.expiresAt());
        m.put("active",    b.active());
        return m;
    }

    private void sendError(WebSocketSession session, String reason) {
        session.send(router.buildPacket(PacketType.MOD_ERROR, Map.of("reason", reason)));
    }
}
