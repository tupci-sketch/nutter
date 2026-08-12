package com.habnut.emulator.room;

import java.util.List;
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
