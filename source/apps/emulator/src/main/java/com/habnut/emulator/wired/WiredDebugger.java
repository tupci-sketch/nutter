package com.habnut.emulator.wired;

import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.SessionRegistry;
import com.habnut.emulator.protocol.PacketType;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WiredDebugger {

    // roomId → set of userIds watching debug stream
    private final ConcurrentHashMap<Long, Set<Long>> watchers = new ConcurrentHashMap<>();
    private final SessionRegistry sessions;
    private final PacketRouter router;

    public WiredDebugger(SessionRegistry sessions, PacketRouter router) {
        this.sessions = sessions;
        this.router   = router;
    }

    public void startWatch(long roomId, long userId) {
        watchers.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    public void stopWatch(long roomId, long userId) {
        Set<Long> ws = watchers.get(roomId);
        if (ws != null) ws.remove(userId);
    }

    public void logStep(long roomId, String stepType, String code, boolean result) {
        Set<Long> ws = watchers.get(roomId);
        if (ws == null || ws.isEmpty()) return;
        String packet = router.buildPacket(PacketType.WIRED_DEBUG_EVENT, Map.of(
            "stepType", stepType, "code", code, "result", result,
            "ts", System.currentTimeMillis()));
        for (long uid : ws) {
            sessions.byUserId(uid).ifPresent(s -> s.send(packet));
        }
    }

    public void logExecution(long roomId, long stackId, boolean triggered) {
        Set<Long> ws = watchers.get(roomId);
        if (ws == null || ws.isEmpty()) return;
        String packet = router.buildPacket(PacketType.WIRED_EXECUTION_LOG, Map.of(
            "stackId", stackId, "triggered", triggered, "ts", System.currentTimeMillis()));
        for (long uid : ws) {
            sessions.byUserId(uid).ifPresent(s -> s.send(packet));
        }
    }
}
