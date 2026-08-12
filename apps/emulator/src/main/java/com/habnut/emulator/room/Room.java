package com.habnut.emulator.room;

import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.net.SessionRegistry;
import com.habnut.emulator.protocol.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class Room {

    private static final Logger log = LoggerFactory.getLogger(Room.class);

    public static final int TICK_INTERVAL_MS = 500;

    private final RoomSettings settings;
    private final RoomModel model;
    private final NavigationGrid grid;
    private final PacketRouter router;
    private final SessionRegistry sessions;

    private final ConcurrentHashMap<Integer, RoomEntity> entities = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, RoomEntity> playerEntities = new ConcurrentHashMap<>();
    private final Set<Long> rights = Collections.synchronizedSet(new HashSet<>());
    private final Set<Long> bans   = Collections.synchronizedSet(new HashSet<>());

    private ScheduledFuture<?> tickTask;
    private volatile boolean active = false;

    public Room(RoomSettings settings, RoomModel model, PacketRouter router,
                SessionRegistry sessions) {
        this.settings = settings;
        this.model    = model;
        this.grid     = model.buildGrid();
        this.router   = router;
        this.sessions = sessions;
    }

    public void startTick(ScheduledExecutorService scheduler) {
        if (active) return;
        active = true;
        tickTask = scheduler.scheduleAtFixedRate(
            this::tick, TICK_INTERVAL_MS, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void stopTick() {
        active = false;
        if (tickTask != null) tickTask.cancel(false);
    }

    private void tick() {
        try {
            tickMovement();
        } catch (Exception e) {
            log.warn("Room {} tick error", settings.id(), e);
        }
    }

    private void tickMovement() {
        List<Map<String, Object>> moves = new ArrayList<>();
        for (RoomEntity entity : entities.values()) {
            if (!entity.isWalking()) continue;
            int[] step = entity.nextWalkStep();
            if (step == null) continue;

            int nx = step[0], ny = step[1];
            double nz = grid.getHeight(nx, ny);
            Position from = entity.getPosition();
            int rot = from.rotationTo(new Position(nx, ny, nz, 0));
            entity.setPosition(new Position(nx, ny, nz, rot));

            moves.add(Map.of(
                "instanceId", entity.instanceId,
                "x", nx, "y", ny, "z", nz, "rotation", rot
            ));
        }
        if (!moves.isEmpty()) {
            broadcast(PacketType.ROOM_USER_MOVED, Map.of("moves", moves));
        }
    }

    public RoomEntity addPlayer(long userId, String username, String figureString,
                                Position spawn) {
        RoomEntity entity = new RoomEntity(RoomEntity.Type.PLAYER, userId, username,
            figureString, spawn);
        entities.put(entity.instanceId, entity);
        playerEntities.put(userId, entity);
        return entity;
    }

    public void removePlayer(long userId) {
        RoomEntity entity = playerEntities.remove(userId);
        if (entity != null) {
            entities.remove(entity.instanceId);
            broadcast(PacketType.ROOM_USER_LEFT, Map.of("instanceId", entity.instanceId));
        }
    }

    public boolean moveEntity(int instanceId, int targetX, int targetY) {
        RoomEntity entity = entities.get(instanceId);
        if (entity == null) return false;
        if (!grid.isWalkable(targetX, targetY)) return false;

        Position pos = entity.getPosition();
        List<int[]> path = grid.findPath(pos.x(), pos.y(), targetX, targetY);
        entity.walkTo(path);
        return !path.isEmpty();
    }

    public List<Map<String, Object>> getEntityState() {
        return entities.values().stream().map(e -> {
            Position p = e.getPosition();
            return (Map<String, Object>) Map.of(
                "instanceId",   e.instanceId,
                "type",         e.type.name().toLowerCase(),
                "sourceId",     e.sourceId,
                "name",         e.name,
                "figureString", e.figureString,
                "x", p.x(), "y", p.y(), "z", p.z(), "rotation", p.rotation()
            );
        }).collect(Collectors.toList());
    }

    public void broadcast(String packetType, Object payload) {
        String json = router.buildPacket(packetType, payload);
        playerEntities.keySet().forEach(uid ->
            sessions.byUserId(uid).ifPresent(s -> s.send(json)));
    }

    public void sendTo(long userId, String packetType, Object payload) {
        sessions.byUserId(userId).ifPresent(s ->
            s.send(router.buildPacket(packetType, payload)));
    }

    public boolean hasRight(long userId) { return userId == settings.ownerId() || rights.contains(userId); }
    public boolean isBanned(long userId) { return bans.contains(userId); }
    public void grantRight(long userId)  { rights.add(userId); }
    public void removeRight(long userId) { rights.remove(userId); }
    public void addBan(long userId)      { bans.add(userId); }

    public RoomSettings getSettings()  { return settings; }
    public RoomModel getModel()        { return model; }
    public NavigationGrid getGrid()    { return grid; }
    public long getId()                { return settings.id(); }
    public int  getEntityCount()       { return entities.size(); }
    public int  getPlayerCount()       { return playerEntities.size(); }
    public boolean isEmpty()           { return playerEntities.isEmpty(); }

    public RoomEntity getEntityForUser(long userId) {
        return playerEntities.get(userId);
    }
}
