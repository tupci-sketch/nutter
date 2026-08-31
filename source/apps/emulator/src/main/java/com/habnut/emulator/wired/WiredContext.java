package com.habnut.emulator.wired;

import com.habnut.emulator.room.Room;
import com.habnut.emulator.room.RoomEntity;

import java.util.*;

public final class WiredContext {

    public enum Scope { ROOM, USER, GLOBAL }

    public final long roomId;
    public final Room room;
    public final RoomEntity triggeringEntity;
    public final Map<String, Object> locals = new HashMap<>();

    private final WiredVariableStore variables;
    private final WiredSignalBus signalBus;
    private final long startNano;
    private int opCount;
    private static final int MAX_OPS = 5_000;

    public WiredContext(long roomId, Room room, RoomEntity triggeringEntity,
                        WiredVariableStore variables, WiredSignalBus signalBus) {
        this.roomId          = roomId;
        this.room            = room;
        this.triggeringEntity = triggeringEntity;
        this.variables       = variables;
        this.signalBus       = signalBus;
        this.startNano       = System.nanoTime();
    }

    public void tick() {
        if (++opCount > MAX_OPS) throw new WiredExecutionLimitException("Op limit reached");
    }

    public long elapsedMs() { return (System.nanoTime() - startNano) / 1_000_000; }

    // Variable accessors forwarded to store
    public WiredValue getVariable(Scope scope, String name) {
        return variables.get(scope, roomId, triggeringEntity != null ? triggeringEntity.getUserId() : -1, name);
    }

    public void setVariable(Scope scope, String name, WiredValue value) {
        variables.set(scope, roomId, triggeringEntity != null ? triggeringEntity.getUserId() : -1, name, value);
    }

    public void emitSignal(String channel, WiredValue payload) {
        signalBus.emit(roomId, channel, payload);
    }
}
