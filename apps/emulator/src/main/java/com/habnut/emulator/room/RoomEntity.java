package com.habnut.emulator.room;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class RoomEntity {

    public enum Type { PLAYER, PET, BOT }

    private static final AtomicInteger ID_SEQ = new AtomicInteger(1);

    public final int instanceId;
    public final Type type;
    public final long sourceId;
    public final String name;
    public final String figureString;

    private final AtomicReference<Position> position;
    private volatile List<int[]> walkPath = List.of();
    private volatile int walkIndex = 0;
    private volatile boolean walking = false;
    private volatile boolean sitting = false;
    private volatile boolean laying  = false;
    private volatile String status   = "";
    private volatile String team     = null;
    private final Map<String, String> statusMap = new ConcurrentHashMap<>();

    // Expression state. Each of these is visible to every other occupant of the
    // room and is cleared when the entity leaves.
    private volatile int effectId    = 0;   // 0 = no effect
    private volatile int danceId     = 0;   // 0 = standing still
    private volatile int handItemId  = 0;   // 0 = empty handed
    private volatile long handItemExpiry = 0;
    private volatile int signId      = -1;  // -1 = no sign held
    private volatile long signExpiry = 0;

    public RoomEntity(Type type, long sourceId, String name,
                      String figureString, Position spawn) {
        this.instanceId   = ID_SEQ.getAndIncrement();
        this.type         = type;
        this.sourceId     = sourceId;
        this.name         = name;
        this.figureString = figureString;
        this.position     = new AtomicReference<>(spawn);
    }

    public Position getPosition() { return position.get(); }

    public void setPosition(Position p) { position.set(p); }

    public void walkTo(List<int[]> path) {
        this.walkPath  = path;
        this.walkIndex = 0;
        this.walking   = !path.isEmpty();
    }

    public boolean isWalking() { return walking; }

    public boolean isSitting() { return sitting; }

    public void setSitting(boolean v) { this.sitting = v; }

    public void setLaying(boolean v) { this.laying = v; }

    public boolean isLaying() { return laying; }

    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }

    public void setStatus(String key, String value) { statusMap.put(key, value); }
    public void clearStatus(String key) { statusMap.remove(key); }
    public Map<String, String> getStatusMap() { return Collections.unmodifiableMap(statusMap); }

    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }

    // ─── expression state ───────────────────────────────────────────────────

    /** How long a hand item or sign stays visible before it clears itself. */
    public static final long HAND_ITEM_DURATION_MS = 30_000;
    public static final long SIGN_DURATION_MS      = 5_000;

    public int getEffectId() { return effectId; }
    public void setEffectId(int id) { this.effectId = Math.max(0, id); }

    public int getDanceId() { return danceId; }

    /**
     * Dances 0-4 are the standard set; anything outside that range is ignored
     * so a crafted packet cannot put an entity into an undefined animation.
     * Sitting or laying entities cannot dance.
     */
    public void setDanceId(int id) {
        if (id < 0 || id > 4) return;
        if (id > 0 && (sitting || laying)) return;
        this.danceId = id;
    }

    public int getHandItemId() {
        if (handItemId != 0 && System.currentTimeMillis() > handItemExpiry) {
            handItemId = 0;
        }
        return handItemId;
    }

    public void setHandItem(int id) {
        this.handItemId = Math.max(0, id);
        this.handItemExpiry = id > 0 ? System.currentTimeMillis() + HAND_ITEM_DURATION_MS : 0;
    }

    public int getSignId() {
        if (signId >= 0 && System.currentTimeMillis() > signExpiry) {
            signId = -1;
        }
        return signId;
    }

    /** Signs 0-17 are the standard set; other values clear the sign. */
    public void setSign(int id) {
        if (id < 0 || id > 17) {
            this.signId = -1;
            this.signExpiry = 0;
            return;
        }
        this.signId = id;
        this.signExpiry = System.currentTimeMillis() + SIGN_DURATION_MS;
    }

    /** Clears every transient expression, used when an entity sits or leaves. */
    public void clearExpressions() {
        this.danceId = 0;
        this.handItemId = 0;
        this.handItemExpiry = 0;
        this.signId = -1;
        this.signExpiry = 0;
    }

    public long getUserId() { return sourceId; }

    public void clearPath() { walkPath = List.of(); walkIndex = 0; walking = false; }

    public int[] nextWalkStep() {
        if (!walking || walkIndex >= walkPath.size()) {
            walking = false;
            return null;
        }
        int[] step = walkPath.get(walkIndex++);
        if (walkIndex >= walkPath.size()) walking = false;
        return step;
    }
}
