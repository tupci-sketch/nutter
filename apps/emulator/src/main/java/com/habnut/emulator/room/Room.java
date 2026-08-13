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
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("instanceId",   e.instanceId);
            m.put("type",         e.type.name().toLowerCase());
            m.put("sourceId",     e.sourceId);
            m.put("name",         e.name);
            m.put("figureString", e.figureString);
            m.put("x", p.x()); m.put("y", p.y()); m.put("z", p.z()); m.put("rotation", p.rotation());
            return m;
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

    public long getOwnerId() { return settings.ownerId(); }

    // --- Wired-callable entity/user operations ---

    public List<Long> getAllUserIds() {
        return List.copyOf(playerEntities.keySet());
    }

    public List<Long> getUsersOnFurni(long furniId) {
        // Implementation delegates to furni store via callback
        if (furniUserQuery != null) return furniUserQuery.apply(furniId);
        return List.of();
    }

    public List<Long> getUsersInArea(int x1, int y1, int x2, int y2) {
        List<Long> result = new ArrayList<>();
        for (Map.Entry<Long, RoomEntity> entry : playerEntities.entrySet()) {
            Position p = entry.getValue().getPosition();
            if (p.x() >= x1 && p.x() <= x2 && p.y() >= y1 && p.y() <= y2)
                result.add(entry.getKey());
        }
        return result;
    }

    public List<Long> getUsersInTeam(String team) {
        List<Long> result = new ArrayList<>();
        for (Map.Entry<Long, RoomEntity> entry : playerEntities.entrySet()) {
            if (team.equals(entry.getValue().getTeam())) result.add(entry.getKey());
        }
        return result;
    }

    public List<Long> getUsersWithBadge(String badge) { return List.of(); }
    public List<Long> getBotIds() {
        List<Long> result = new ArrayList<>();
        for (RoomEntity e : entities.values()) {
            if (e.type == RoomEntity.Type.BOT) result.add(e.sourceId);
        }
        return result;
    }

    public void teleportUser(long userId, int x, int y) {
        RoomEntity entity = playerEntities.get(userId);
        if (entity == null || !grid.isWalkable(x, y)) return;
        double z = grid.getHeight(x, y);
        entity.clearPath();
        entity.setPosition(new Position(x, y, z, entity.getPosition().rotation()));
        broadcast(PacketType.ROOM_USER_MOVED, Map.of("moves", List.of(Map.of(
            "instanceId", entity.instanceId, "x", x, "y", y, "z", z,
            "rotation", entity.getPosition().rotation()))));
    }

    public void kickUser(long userId, String reason) {
        sessions.byUserId(userId).ifPresent(s -> {
            s.send(router.buildPacket(PacketType.ROOM_USER_KICKED,
                Map.of("reason", reason)));
            s.close();
        });
    }

    public void sitUser(long userId)  { setUserStatus(userId, "sit"); }
    public void standUser(long userId){ clearUserStatus(userId, "sit"); }
    public void waveUser(long userId) { setUserStatus(userId, "wave"); }
    public void danceUser(long userId, int danceId) {
        RoomEntity e = playerEntities.get(userId);
        if (e != null) { e.setStatus("dance", String.valueOf(danceId)); broadcastUserStatus(e); }
    }

    public void setUserEffect(long userId, int effectId) {
        broadcast(PacketType.ROOM_USER_MOVED, Map.of("effectId", effectId, "userId", userId));
    }

    public void broadcastWiredChat(String msg) {
        broadcast(PacketType.ROOM_USER_CHAT,
            Map.of("instanceId", -1, "message", msg, "bubble", 0));
    }

    public void whisperUser(long userId, String msg) {
        sendTo(userId, PacketType.ROOM_USER_WHISPER,
            Map.of("instanceId", -1, "message", msg));
    }

    public void sendNotification(long userId, String msg) {
        sendTo(userId, PacketType.SYSTEM_NOTICE, Map.of("message", msg));
    }

    public void sendOpenLink(long userId, String url) {
        sendTo(userId, "room.open_link", Map.of("url", url));
    }

    // --- Wired-callable furni operations (delegated to FurniOperations callback) ---

    @FunctionalInterface public interface FurniUserQuery  { List<Long> apply(long furniId); }
    @FunctionalInterface public interface FurniOp         { void execute(long furniId, Object arg); }
    @FunctionalInterface public interface FurniQuery      { Optional<int[]> apply(long furniId); }
    @FunctionalInterface public interface FurniListQuery  { List<Long> apply(Object arg); }
    @FunctionalInterface public interface BotOp           { void execute(long botId, Object arg); }

    private FurniUserQuery  furniUserQuery;
    private FurniOp         moveFurniOp;
    private FurniOp         setFurniStateOp;
    private FurniOp         toggleFurniStateOp;
    private FurniOp         resetFurniStateOp;
    private FurniOp         rotateFurniOp;
    private FurniOp         moveFurniToOp;
    private FurniQuery      furniPositionQuery;
    private FurniOp         setFurniDataOp;
    private FurniOp         toggleStickyPoleOp;
    private FurniListQuery  furniByTypeQuery;
    private FurniListQuery  furniByStateQuery;
    private FurniListQuery  closestFurniQuery;
    private BotOp           moveBotOp;
    private BotOp           botChatOp;

    public void setFurniCallbacks(FurniUserQuery userQuery, FurniOp moveOp, FurniOp stateOp,
                                   FurniOp toggleOp, FurniOp resetOp, FurniOp rotateOp,
                                   FurniQuery posQuery, FurniOp dataOp, FurniOp stickyOp,
                                   FurniListQuery typeQuery, FurniListQuery stateQuery,
                                   FurniListQuery closestQuery, FurniOp moveToOp,
                                   BotOp moveBotFn, BotOp chatBotFn) {
        this.furniUserQuery     = userQuery;
        this.moveFurniOp        = moveOp;
        this.setFurniStateOp    = stateOp;
        this.toggleFurniStateOp = toggleOp;
        this.resetFurniStateOp  = resetOp;
        this.rotateFurniOp      = rotateOp;
        this.furniPositionQuery = posQuery;
        this.setFurniDataOp     = dataOp;
        this.toggleStickyPoleOp = stickyOp;
        this.furniByTypeQuery   = typeQuery;
        this.furniByStateQuery  = stateQuery;
        this.closestFurniQuery  = closestQuery;
        this.moveFurniToOp      = moveToOp;
        this.moveBotOp          = moveBotFn;
        this.botChatOp          = chatBotFn;
    }

    public void moveFurni(long furniId, int dx, int dy) {
        if (moveFurniOp != null) moveFurniOp.execute(furniId, new int[]{dx, dy});
    }
    public void setFurniState(long furniId, int state) {
        if (setFurniStateOp != null) setFurniStateOp.execute(furniId, state);
    }
    public void toggleFurniState(long furniId) {
        if (toggleFurniStateOp != null) toggleFurniStateOp.execute(furniId, null);
    }
    public void resetFurniState(long furniId) {
        if (resetFurniStateOp != null) resetFurniStateOp.execute(furniId, null);
    }
    public void rotateFurni(long furniId, int rotation) {
        if (rotateFurniOp != null) rotateFurniOp.execute(furniId, rotation);
    }
    public void moveFurniTo(long furniId, Position pos) {
        if (moveFurniToOp != null) moveFurniToOp.execute(furniId, pos);
    }
    public Optional<int[]> getFurniPosition(long furniId) {
        return furniPositionQuery != null ? furniPositionQuery.apply(furniId) : Optional.empty();
    }
    public List<Long> getAllFurniIds() {
        if (furniByTypeQuery != null) return furniByTypeQuery.apply(null);
        return List.of();
    }
    public List<Long> getFurniByType(String type) {
        if (furniByTypeQuery != null) return furniByTypeQuery.apply(type);
        return List.of();
    }
    public List<Long> getFurniWithState(int state) {
        if (furniByStateQuery != null) return furniByStateQuery.apply(state);
        return List.of();
    }
    public List<Long> getClosestFurni(Position pos, List<Long> candidates) {
        if (closestFurniQuery != null) return closestFurniQuery.apply(new Object[]{pos, candidates});
        return candidates.isEmpty() ? List.of() : List.of(candidates.get(0));
    }
    public void setFurniData(long furniId, String data) {
        if (setFurniDataOp != null) setFurniDataOp.execute(furniId, data);
    }
    public void toggleStickyPole(long furniId) {
        if (toggleStickyPoleOp != null) toggleStickyPoleOp.execute(furniId, null);
    }
    public void moveBotTo(long botId, int x, int y) {
        if (moveBotOp != null) moveBotOp.execute(botId, new int[]{x, y});
    }
    public void botChat(long botId, String message) {
        if (botChatOp != null) botChatOp.execute(botId, message);
    }

    public void setLighting(String preset) {
        broadcast("room.lighting.changed", Map.of("preset", preset));
    }
    public void setMusicTrack(int trackId) {
        broadcast("room.music.changed", Map.of("trackId", trackId));
    }
    public void setOwnerEffect(int effectId) {
        sendTo(settings.ownerId(), "room.owner.effect", Map.of("effectId", effectId));
    }

    private void setUserStatus(long userId, String status) {
        RoomEntity e = playerEntities.get(userId);
        if (e != null) { e.setStatus(status, "1"); broadcastUserStatus(e); }
    }
    private void clearUserStatus(long userId, String status) {
        RoomEntity e = playerEntities.get(userId);
        if (e != null) { e.clearStatus(status); broadcastUserStatus(e); }
    }
    private void broadcastUserStatus(RoomEntity e) {
        broadcast(PacketType.ROOM_USER_MOVED, Map.of(
            "instanceId", e.instanceId,
            "status", e.getStatusMap()));
    }
}
