package com.habnut.emulator.bot;

import com.fasterxml.jackson.databind.JsonNode;
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

public final class BotHandler {

    private static final Logger log = LoggerFactory.getLogger(BotHandler.class);

    private final BotService    botService;
    private final RoomManager   roomManager;
    private final SessionRegistry sessions;
    private final PacketRouter  router;

    public BotHandler(BotService botService, RoomManager roomManager,
                      SessionRegistry sessions, PacketRouter router) {
        this.botService  = botService;
        this.roomManager = roomManager;
        this.sessions    = sessions;
        this.router      = router;
    }

    public void register(PacketRouter router) {
        router.register(PacketType.BOT_PLACE,   this::handlePlace);
        router.register(PacketType.BOT_PICKUP,  this::handlePickup);
        router.register(PacketType.BOT_UPDATE,  this::handleUpdate);
        router.register(PacketType.BOT_COMMAND, this::handleCommand);
    }

    private void handlePlace(WebSocketSession session, JsonNode p) {
        long botId  = p.path("botId").asLong(-1);
        long roomId = p.path("roomId").asLong(-1);
        int  x      = p.path("x").asInt(5);
        int  y      = p.path("y").asInt(5);
        long userId = session.getUserId();

        Optional<Room> roomOpt = roomManager.get(roomId);
        if (roomOpt.isEmpty()) { sendError(session, "room_not_found"); return; }
        Room room = roomOpt.get();

        try {
            BotService.PlaceResult result = botService.place(botId, userId, roomId, x, y);
            if (!result.ok()) { sendError(session, result.reason()); return; }

            BotService.Bot bot = result.bot();
            Map<String, Object> payload = buildBotPayload(bot);
            String json = router.buildPacket(PacketType.BOT_PLACED, payload);
            for (long uid : room.getAllUserIds())
                sessions.byUserId(uid).ifPresent(s -> s.send(json));
        } catch (SQLException e) {
            log.error("Bot place error", e);
            sendError(session, "server_error");
        }
    }

    private void handlePickup(WebSocketSession session, JsonNode p) {
        long botId  = p.path("botId").asLong(-1);
        long userId = session.getUserId();

        try {
            Optional<BotService.Bot> opt = botService.getBot(botId);
            if (opt.isEmpty()) { sendError(session, "not_found"); return; }

            Long roomId = opt.get().currentRoomId();
            boolean ok  = botService.pickup(botId, userId);
            if (!ok) { sendError(session, "permission_denied"); return; }

            if (roomId != null) {
                roomManager.get(roomId).ifPresent(room -> {
                    String json = router.buildPacket(PacketType.BOT_PICKED_UP,
                        Map.of("botId", botId, "ownerId", userId));
                    for (long uid : room.getAllUserIds())
                        sessions.byUserId(uid).ifPresent(s -> s.send(json));
                });
            }
        } catch (SQLException e) {
            log.error("Bot pickup error", e);
            sendError(session, "server_error");
        }
    }

    private void handleUpdate(WebSocketSession session, JsonNode p) {
        long   botId       = p.path("botId").asLong(-1);
        String name        = p.path("name").asText("");
        String motto       = p.path("motto").asText("");
        String chatModeStr = p.path("chatMode").asText("NORMAL");
        String walkModeStr = p.path("walkMode").asText("STAND");
        long   userId      = session.getUserId();

        try {
            boolean ok = botService.updateSettings(botId, userId, name, motto, chatModeStr, walkModeStr);
            if (!ok) { sendError(session, "permission_denied"); return; }

            Optional<BotService.Bot> opt = botService.getBot(botId);
            if (opt.isEmpty()) return;
            BotService.Bot bot = opt.get();

            if (bot.currentRoomId() != null) {
                roomManager.get(bot.currentRoomId()).ifPresent(room -> {
                    String json = router.buildPacket(PacketType.BOT_MOVED, buildBotPayload(bot));
                    for (long uid : room.getAllUserIds())
                        sessions.byUserId(uid).ifPresent(s -> s.send(json));
                });
            }
        } catch (SQLException e) {
            log.error("Bot update error", e);
            sendError(session, "server_error");
        }
    }

    private void handleCommand(WebSocketSession session, JsonNode p) {
        long   botId   = p.path("botId").asLong(-1);
        String command = p.path("command").asText("");
        String trigger = p.path("trigger").asText("");

        try {
            Optional<BotService.Bot> opt = botService.getBot(botId);
            if (opt.isEmpty()) { sendError(session, "not_found"); return; }
            BotService.Bot bot = opt.get();

            if (bot.currentRoomId() == null) return;
            Room room = roomManager.get(bot.currentRoomId()).orElse(null);
            if (room == null) return;

            String chatMsg = "say".equals(command) ? botService.handleChat(bot, trigger) : null;
            if (chatMsg != null) {
                String json = router.buildPacket(PacketType.BOT_CHAT,
                    Map.of("botId", botId, "name", bot.name(), "message", chatMsg));
                for (long uid : room.getAllUserIds())
                    sessions.byUserId(uid).ifPresent(s -> s.send(json));
            }
        } catch (SQLException e) {
            log.error("Bot command error", e);
            sendError(session, "server_error");
        }
    }

    private Map<String, Object> buildBotPayload(BotService.Bot bot) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",       bot.id());
        m.put("ownerId",  bot.ownerId());
        m.put("name",     bot.name());
        m.put("figure",   bot.figure());
        m.put("motto",    bot.motto());
        m.put("chatMode", bot.chatMode().name());
        m.put("walkMode", bot.walkMode().name());
        m.put("gender",   bot.gender());
        m.put("x",        bot.posX());
        m.put("y",        bot.posY());
        return m;
    }

    private void sendError(WebSocketSession session, String reason) {
        session.send(router.buildPacket(PacketType.BOT_ERROR, Map.of("reason", reason)));
    }
}
