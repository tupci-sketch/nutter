package com.habnut.emulator.furni;

import com.fasterxml.jackson.databind.JsonNode;
import com.habnut.emulator.db.DatabaseManager;
import com.habnut.emulator.metrics.MetricsRegistry;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.RateLimiter;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.protocol.ErrorCode;
import com.habnut.emulator.protocol.PacketType;
import com.habnut.emulator.room.Room;
import com.habnut.emulator.room.RoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

public final class FurniHandler {

    private static final Logger log = LoggerFactory.getLogger(FurniHandler.class);

    private final RoomManager rooms;
    private final FurniBaseRepository bases;
    private final DatabaseManager db;
    private final PacketRouter router;
    private final RateLimiter rateLimiter;

    private final java.util.concurrent.ConcurrentHashMap<Long, RoomFurniStore> stores =
        new java.util.concurrent.ConcurrentHashMap<>();

    public FurniHandler(RoomManager rooms, FurniBaseRepository bases,
                        DatabaseManager db, PacketRouter router, RateLimiter rateLimiter) {
        this.rooms       = rooms;
        this.bases       = bases;
        this.db          = db;
        this.router      = router;
        this.rateLimiter = rateLimiter;
    }

    public void register(PacketRouter router) {
        router.register(PacketType.FURNI_PLACE,      this::handlePlace);
        router.register(PacketType.FURNI_MOVE,       this::handleMove);
        router.register(PacketType.FURNI_ROTATE,     this::handleRotate);
        router.register(PacketType.FURNI_PICKUP,     this::handlePickup);
        router.register(PacketType.FURNI_INTERACT,   this::handleInteract);
        router.register(PacketType.FURNI_WALL_PLACE, this::handleWallPlace);
        router.register(PacketType.FURNI_WALL_MOVE,  this::handleWallMove);
        router.register(PacketType.FURNI_WALL_PICKUP,this::handleWallPickup);
    }

    private RoomFurniStore storeFor(long roomId) {
        return stores.computeIfAbsent(roomId, id -> {
            RoomFurniStore store = new RoomFurniStore(db, bases, id);
            store.load();
            return store;
        });
    }

    public void evictRoom(long roomId) {
        stores.remove(roomId);
    }

    private void handlePlace(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        if (!rateLimiter.tryAcquire(session.sessionId, RateLimiter.Bucket.FURNI)) return;

        long userId  = session.getUserId();
        long roomId  = payload.path("roomId").asLong(-1);
        long invId   = payload.path("inventoryId").asLong(-1);
        long baseId  = payload.path("baseId").asLong(-1);
        int x        = payload.path("x").asInt(-1);
        int y        = payload.path("y").asInt(-1);
        int rotation = payload.path("rotation").asInt(0);

        Room room = roomOrError(session, roomId);
        if (room == null) return;
        if (!room.hasRight(userId)) { sendError(session, ErrorCode.ROOM_ACCESS_DENIED, "No rights"); return; }

        double z = room.getGrid().getHeight(x, y);
        RoomFurniStore store = storeFor(roomId);
        try {
            long newId = store.placeFloor(invId, userId, baseId, x, y, z, rotation);
            FloorItem item = store.getFloor(newId).orElseThrow();
            room.broadcast(PacketType.FURNI_PLACED, floorItemMap(item, newId));
        } catch (Exception e) {
            log.warn("Place floor furni failed: userId={} roomId={}", userId, roomId, e);
            sendError(session, ErrorCode.GENERIC_INTERNAL_ERROR, "Placement failed");
        }
    }

    private void handleMove(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        if (!rateLimiter.tryAcquire(session.sessionId, RateLimiter.Bucket.FURNI)) return;

        long userId = session.getUserId();
        long roomId = payload.path("roomId").asLong(-1);
        long itemId = payload.path("itemId").asLong(-1);
        int x       = payload.path("x").asInt(-1);
        int y       = payload.path("y").asInt(-1);
        int rot     = payload.path("rotation").asInt(0);

        Room room = roomOrError(session, roomId);
        if (room == null) return;
        if (!room.hasRight(userId)) { sendError(session, ErrorCode.ROOM_ACCESS_DENIED, "No rights"); return; }

        RoomFurniStore store = storeFor(roomId);
        try {
            double z = room.getGrid().getHeight(x, y);
            store.moveFloor(itemId, x, y, z, rot);
            room.broadcast(PacketType.FURNI_MOVED, Map.of("itemId", itemId, "x", x, "y", y, "z", z, "rotation", rot));
        } catch (Exception e) {
            log.warn("Move furni failed itemId={}", itemId, e);
            sendError(session, ErrorCode.GENERIC_INTERNAL_ERROR, "Move failed");
        }
    }

    private void handleRotate(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        if (!rateLimiter.tryAcquire(session.sessionId, RateLimiter.Bucket.FURNI)) return;

        long userId = session.getUserId();
        long roomId = payload.path("roomId").asLong(-1);
        long itemId = payload.path("itemId").asLong(-1);

        Room room = roomOrError(session, roomId);
        if (room == null) return;
        if (!room.hasRight(userId)) { sendError(session, ErrorCode.ROOM_ACCESS_DENIED, "No rights"); return; }

        RoomFurniStore store = storeFor(roomId);
        store.getFloor(itemId).ifPresent(item -> {
            int newRot = (item.getRotation() + 2) % 8;
            try {
                store.moveFloor(itemId, item.getX(), item.getY(), item.getZ(), newRot);
                room.broadcast(PacketType.FURNI_ROTATED,
                    Map.of("itemId", itemId, "rotation", newRot));
            } catch (Exception e) {
                log.warn("Rotate furni failed itemId={}", itemId, e);
            }
        });
    }

    private void handlePickup(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;

        long userId = session.getUserId();
        long roomId = payload.path("roomId").asLong(-1);
        long itemId = payload.path("itemId").asLong(-1);

        Room room = roomOrError(session, roomId);
        if (room == null) return;

        RoomFurniStore store = storeFor(roomId);
        store.getFloor(itemId).ifPresent(item -> {
            if (item.ownerId != userId && !room.hasRight(userId)) {
                sendError(session, ErrorCode.ROOM_ACCESS_DENIED, "Not owner");
                return;
            }
            try {
                long invId = store.pickupFloor(itemId, userId);
                room.broadcast(PacketType.FURNI_PICKED_UP, Map.of("itemId", itemId));
                session.send(router.buildPacket(PacketType.FURNI_PICKED_UP,
                    Map.of("itemId", itemId, "inventoryId", invId)));
            } catch (Exception e) {
                log.warn("Pickup furni failed itemId={}", itemId, e);
                sendError(session, ErrorCode.GENERIC_INTERNAL_ERROR, "Pickup failed");
            }
        });
    }

    private void handleInteract(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;

        long userId = session.getUserId();
        long roomId = payload.path("roomId").asLong(-1);
        long itemId = payload.path("itemId").asLong(-1);

        Room room = roomOrError(session, roomId);
        if (room == null) return;

        RoomFurniStore store = storeFor(roomId);
        store.getFloor(itemId).ifPresent(item -> {
            int newState = item.nextState();
            room.broadcast(PacketType.FURNI_STATE_CHANGED,
                Map.of("itemId", itemId, "state", newState));
        });
    }

    private void handleWallPlace(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        if (!rateLimiter.tryAcquire(session.sessionId, RateLimiter.Bucket.FURNI)) return;

        long userId = session.getUserId();
        long roomId = payload.path("roomId").asLong(-1);
        long invId  = payload.path("inventoryId").asLong(-1);
        long baseId = payload.path("baseId").asLong(-1);
        String pos  = payload.path("wallPosition").asText(":w=0,0 l=0,0 l");

        Room room = roomOrError(session, roomId);
        if (room == null) return;
        if (!room.hasRight(userId)) { sendError(session, ErrorCode.ROOM_ACCESS_DENIED, "No rights"); return; }

        RoomFurniStore store = storeFor(roomId);
        try {
            store.placeWall(invId, userId, baseId, pos);
            room.broadcast(PacketType.FURNI_WALL_PLACED,
                Map.of("baseId", baseId, "wallPosition", pos, "ownerId", userId));
        } catch (Exception e) {
            log.warn("Wall place failed userId={}", userId, e);
            sendError(session, ErrorCode.GENERIC_INTERNAL_ERROR, "Placement failed");
        }
    }

    private void handleWallMove(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        if (!rateLimiter.tryAcquire(session.sessionId, RateLimiter.Bucket.FURNI)) return;

        long userId = session.getUserId();
        long roomId = payload.path("roomId").asLong(-1);
        long itemId = payload.path("itemId").asLong(-1);
        String pos  = payload.path("wallPosition").asText(null);

        Room room = roomOrError(session, roomId);
        if (room == null) return;
        if (!room.hasRight(userId)) { sendError(session, ErrorCode.ROOM_ACCESS_DENIED, "No rights"); return; }

        RoomFurniStore store = storeFor(roomId);
        store.getWall(itemId).ifPresent(item -> {
            if (pos != null) item.setWallPosition(pos);
            room.broadcast(PacketType.FURNI_WALL_MOVED,
                Map.of("itemId", itemId, "wallPosition", item.getWallPosition()));
        });
    }

    private void handleWallPickup(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;

        long userId = session.getUserId();
        long roomId = payload.path("roomId").asLong(-1);
        long itemId = payload.path("itemId").asLong(-1);

        Room room = roomOrError(session, roomId);
        if (room == null) return;

        RoomFurniStore store = storeFor(roomId);
        store.getWall(itemId).ifPresent(item -> {
            if (item.ownerId != userId && !room.hasRight(userId)) {
                sendError(session, ErrorCode.ROOM_ACCESS_DENIED, "Not owner");
                return;
            }
            room.broadcast(PacketType.FURNI_WALL_PICKED_UP, Map.of("itemId", itemId));
        });
    }

    private Room roomOrError(WebSocketSession session, long roomId) {
        if (roomId < 1) { sendError(session, ErrorCode.ROOM_NOT_FOUND, "Invalid roomId"); return null; }
        var opt = rooms.get(roomId);
        if (opt.isEmpty()) { sendError(session, ErrorCode.ROOM_NOT_FOUND, "Room not loaded"); return null; }
        return opt.get();
    }

    /**
     * Everything standing in a room, for a player who has just walked in.
     *
     * The room's own packet carries the floor and the people; without this it
     * carried no furniture, so a player entering a decorated room saw an empty
     * one until somebody happened to move something.
     */
    public Map<String, Object> roomContents(long roomId) {
        RoomFurniStore store = storeFor(roomId);
        return Map.of(
            "floor", store.allFloor().stream()
                .map(item -> floorItemMap(item, item.id))
                .collect(java.util.stream.Collectors.toList()),
            "wall", store.allWall().stream()
                .map(this::wallItemMap)
                .collect(java.util.stream.Collectors.toList())
        );
    }

    private Map<String, Object> wallItemMap(WallItem item) {
        return Map.of(
            "id", item.id, "baseId", item.base.id(), "spriteId", item.base.spriteId(),
            "wallPosition", item.getWallPosition(), "state", item.getState(),
            "extra", item.getExtra()
        );
    }

    private Map<String, Object> floorItemMap(FloorItem item, long id) {
        return Map.of(
            "id", id, "baseId", item.base.id(), "spriteId", item.base.spriteId(),
            "x", item.getX(), "y", item.getY(), "z", item.getZ(),
            "rotation", item.getRotation(), "state", item.getState(),
            "extra", item.getExtra()
        );
    }

    private void sendError(WebSocketSession session, String code, String message) {
        session.send(router.buildPacket(PacketType.SYSTEM_ERROR, Map.of("code", code, "message", message)));
    }
}
