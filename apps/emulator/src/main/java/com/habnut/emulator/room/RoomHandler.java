package com.habnut.emulator.room;

import com.fasterxml.jackson.databind.JsonNode;
import com.habnut.emulator.auth.UserRepository;
import com.habnut.emulator.metrics.MetricsRegistry;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.RateLimiter;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.protocol.ErrorCode;
import com.habnut.emulator.protocol.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class RoomHandler {

    private static final Logger log = LoggerFactory.getLogger(RoomHandler.class);

    private final RoomManager rooms;
    private final RoomRepository roomRepo;
    private final RoomModelRepository modelRepo;
    private final UserRepository userRepo;
    private final PacketRouter router;
    private final MetricsRegistry metrics;
    private final RateLimiter rateLimiter;

    private final Map<Long, Long> userCurrentRoom = new java.util.concurrent.ConcurrentHashMap<>();

    public RoomHandler(RoomManager rooms, RoomRepository roomRepo,
                       RoomModelRepository modelRepo, UserRepository userRepo,
                       PacketRouter router, MetricsRegistry metrics,
                       RateLimiter rateLimiter) {
        this.rooms      = rooms;
        this.roomRepo   = roomRepo;
        this.modelRepo  = modelRepo;
        this.userRepo   = userRepo;
        this.router     = router;
        this.metrics    = metrics;
        this.rateLimiter = rateLimiter;
    }

    public void register(PacketRouter router) {
        router.register(PacketType.ROOM_ENTER,          this::handleEnter);
        router.register(PacketType.ROOM_LEAVE,          this::handleLeave);
        router.register(PacketType.ROOM_MOVE,           this::handleMove);
        router.register(PacketType.ROOM_CHAT,           this::handleChat);
        router.register(PacketType.ROOM_SHOUT,          this::handleShout);
        router.register(PacketType.ROOM_WHISPER,        this::handleWhisper);
        router.register(PacketType.ROOM_CREATE,         this::handleCreate);
        router.register(PacketType.ROOM_NAV_SEARCH,     this::handleNavSearch);
        router.register(PacketType.ROOM_NAV_MY_ROOMS,   this::handleNavMyRooms);
        router.register(PacketType.ROOM_NAV_POPULAR,    this::handleNavPopular);
    }

    private void handleEnter(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) {
            sendError(session, ErrorCode.AUTH_NOT_AUTHENTICATED, "Not authenticated");
            return;
        }
        long roomId = payload.path("roomId").asLong(-1);
        if (roomId < 1) { sendError(session, ErrorCode.ROOM_NOT_FOUND, "Invalid roomId"); return; }

        long userId = session.getUserId();

        // Leave current room if in one
        Long current = userCurrentRoom.get(userId);
        if (current != null && !current.equals(roomId)) {
            rooms.get(current).ifPresent(r -> {
                r.removePlayer(userId);
                rooms.unloadIfEmpty(current);
            });
        }

        Room room;
        try {
            room = rooms.load(roomId);
        } catch (Exception e) {
            sendError(session, ErrorCode.ROOM_NOT_FOUND, "Room not found");
            return;
        }

        if (room.isBanned(userId)) {
            sendError(session, ErrorCode.ROOM_BANNED, "You are banned from this room");
            return;
        }

        if (room.getPlayerCount() >= room.getSettings().maxVisitors()) {
            sendError(session, ErrorCode.ROOM_FULL, "Room is full");
            return;
        }

        UserRepository.UserRow user = userRepo.findById(userId);
        if (user == null) { sendError(session, ErrorCode.GENERIC_SERVER_ERROR, "User not found"); return; }

        RoomModel model = room.getModel();
        Position spawn  = new Position(model.doorX(), model.doorY(),
            room.getGrid().getHeight(model.doorX(), model.doorY()), model.doorRotation());

        RoomEntity entity = room.addPlayer(userId, user.username(), user.figureString(), spawn);
        userCurrentRoom.put(userId, roomId);

        // Send full room state to the entering player
        session.send(router.buildPacket(PacketType.ROOM_ENTER_SUCCESS, Map.of(
            "roomId",   roomId,
            "name",     room.getSettings().name(),
            "modelId",  model.id(),
            "ownerId",  room.getSettings().ownerId(),
            "ownerName",room.getSettings().ownerName(),
            "entities", room.getEntityState()
        )));

        // Announce new user to existing occupants
        Position p = entity.getPosition();
        room.broadcast(PacketType.ROOM_USER_ENTERED, Map.of(
            "instanceId",   entity.instanceId,
            "userId",       userId,
            "username",     user.username(),
            "figureString", user.figureString(),
            "x", p.x(), "y", p.y(), "z", p.z(), "rotation", p.rotation()
        ));

        log.debug("User {} entered room {}", userId, roomId);
    }

    private void handleLeave(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long userId = session.getUserId();
        Long roomId = userCurrentRoom.remove(userId);
        if (roomId == null) return;
        rooms.get(roomId).ifPresent(r -> {
            r.removePlayer(userId);
            rooms.unloadIfEmpty(roomId);
        });
    }

    private void handleMove(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        if (!rateLimiter.tryAcquire(session.sessionId, RateLimiter.Bucket.MOVEMENT)) return;

        long userId = session.getUserId();
        Long roomId = userCurrentRoom.get(userId);
        if (roomId == null) return;

        rooms.get(roomId).ifPresent(room -> {
            RoomEntity entity = room.getEntityForUser(userId);
            if (entity == null) return;
            int tx = payload.path("x").asInt(-1);
            int ty = payload.path("y").asInt(-1);
            if (tx < 0 || ty < 0) return;
            room.moveEntity(entity.instanceId, tx, ty);
        });
    }

    private void handleChat(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        if (!rateLimiter.tryAcquire(session.sessionId, RateLimiter.Bucket.CHAT)) return;

        long userId = session.getUserId();
        Long roomId = userCurrentRoom.get(userId);
        if (roomId == null) return;

        String message = payload.path("message").asText("").trim();
        if (message.isBlank() || message.length() > 512) return;

        rooms.get(roomId).ifPresent(room -> {
            RoomEntity entity = room.getEntityForUser(userId);
            if (entity == null) return;
            room.broadcast(PacketType.ROOM_USER_CHAT, Map.of(
                "instanceId", entity.instanceId,
                "message",    message,
                "colour",     payload.path("colour").asInt(0)
            ));
            metrics.incrementChatMessages();
        });
    }

    private void handleShout(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        if (!rateLimiter.tryAcquire(session.sessionId, RateLimiter.Bucket.CHAT)) return;

        long userId = session.getUserId();
        Long roomId = userCurrentRoom.get(userId);
        if (roomId == null) return;

        String message = payload.path("message").asText("").trim();
        if (message.isBlank() || message.length() > 512) return;

        rooms.get(roomId).ifPresent(room -> {
            RoomEntity entity = room.getEntityForUser(userId);
            if (entity == null) return;
            room.broadcast(PacketType.ROOM_USER_SHOUT, Map.of(
                "instanceId", entity.instanceId,
                "message",    message
            ));
            metrics.incrementChatMessages();
        });
    }

    private void handleWhisper(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        if (!rateLimiter.tryAcquire(session.sessionId, RateLimiter.Bucket.CHAT)) return;

        long userId = session.getUserId();
        Long roomId = userCurrentRoom.get(userId);
        if (roomId == null) return;

        String targetName = payload.path("targetUsername").asText(null);
        String message    = payload.path("message").asText("").trim();
        if (targetName == null || message.isBlank()) return;

        rooms.get(roomId).ifPresent(room -> {
            RoomEntity senderEntity = room.getEntityForUser(userId);
            if (senderEntity == null) return;
            room.getEntityState().stream()
                .filter(e -> targetName.equals(e.get("name")))
                .findFirst()
                .ifPresent(target -> {
                    long targetId = (long) target.get("sourceId");
                    room.sendTo(targetId, PacketType.ROOM_USER_WHISPER, Map.of(
                        "instanceId", senderEntity.instanceId,
                        "message",    message
                    ));
                });
        });
    }

    private void handleCreate(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) {
            sendError(session, ErrorCode.AUTH_NOT_AUTHENTICATED, "Not authenticated");
            return;
        }
        long userId   = session.getUserId();
        String name   = payload.path("name").asText("").trim();
        String desc   = payload.path("description").asText("").trim();
        String model  = payload.path("modelId").asText("model_a");
        int maxV      = Math.min(50, Math.max(1, payload.path("maxVisitors").asInt(25)));

        if (name.isBlank() || name.length() > 64) {
            sendError(session, ErrorCode.GENERIC_INVALID_PACKET, "Invalid room name");
            return;
        }

        if (modelRepo.find(model).isEmpty()) {
            sendError(session, ErrorCode.GENERIC_INVALID_PACKET, "Invalid model");
            return;
        }

        try {
            long roomId = roomRepo.create(userId, name, desc, model, maxV);
            session.send(router.buildPacket(PacketType.ROOM_CREATED,
                Map.of("roomId", roomId, "name", name)));
        } catch (Exception e) {
            log.error("Room create failed for user {}", userId, e);
            sendError(session, ErrorCode.GENERIC_SERVER_ERROR, "Could not create room");
        }
    }

    private void handleNavSearch(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        String query = payload.path("query").asText("").trim();
        List<Map<String, Object>> results = roomRepo.searchPublic(query, 40)
            .stream().map(this::toNavEntry).collect(Collectors.toList());
        session.send(router.buildPacket(PacketType.ROOM_NAV_SEARCH_RESULT,
            Map.of("query", query, "rooms", results)));
    }

    private void handleNavMyRooms(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        List<Map<String, Object>> results = roomRepo.findByOwner(session.getUserId())
            .stream().map(this::toNavEntry).collect(Collectors.toList());
        session.send(router.buildPacket(PacketType.ROOM_NAV_MY_ROOMS_RESULT,
            Map.of("rooms", results)));
    }

    private void handleNavPopular(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        List<Map<String, Object>> results = roomRepo.getPopular(40)
            .stream().map(this::toNavEntry).collect(Collectors.toList());
        session.send(router.buildPacket(PacketType.ROOM_NAV_POPULAR_RESULT,
            Map.of("rooms", results)));
    }

    private Map<String, Object> toNavEntry(RoomSettings r) {
        int playerCount = rooms.get(r.id()).map(Room::getPlayerCount).orElse(0);
        return Map.of(
            "id",          r.id(),
            "name",        r.name(),
            "ownerName",   r.ownerName(),
            "accessType",  r.accessType(),
            "maxVisitors", r.maxVisitors(),
            "playerCount", playerCount,
            "score",       r.score()
        );
    }

    public void onSessionDisconnect(long userId) {
        Long roomId = userCurrentRoom.remove(userId);
        if (roomId != null) {
            rooms.get(roomId).ifPresent(r -> {
                r.removePlayer(userId);
                rooms.unloadIfEmpty(roomId);
            });
        }
    }

    private void sendError(WebSocketSession session, String code, String message) {
        session.send(router.buildPacket("system.error",
            Map.of("code", code, "message", message)));
    }
}
