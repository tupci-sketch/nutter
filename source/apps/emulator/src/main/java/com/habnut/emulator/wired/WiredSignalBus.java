package com.habnut.emulator.wired;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class WiredSignalBus {

    private static final Logger log = LoggerFactory.getLogger(WiredSignalBus.class);
    private static final long GLOBAL_RATE_WINDOW_MS = 1_000;
    private static final int  GLOBAL_RATE_LIMIT     = 10;

    @FunctionalInterface
    public interface SignalListener { void onSignal(long roomId, String channel, WiredValue payload); }

    private final ConcurrentHashMap<Long, List<SignalListener>> roomListeners = new ConcurrentHashMap<>();

    // Global rate limiter: channel → (windowStart, count)
    private final ConcurrentHashMap<String, long[]> globalRateState = new ConcurrentHashMap<>();

    public void subscribe(long roomId, SignalListener listener) {
        roomListeners.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void unsubscribeAll(long roomId) {
        roomListeners.remove(roomId);
    }

    public void emit(long roomId, String channel, WiredValue payload) {
        List<SignalListener> listeners = roomListeners.get(roomId);
        if (listeners == null || listeners.isEmpty()) return;
        for (SignalListener l : listeners) {
            try { l.onSignal(roomId, channel, payload); }
            catch (Exception e) { log.warn("Signal listener threw: channel={}", channel, e); }
        }
    }

    public boolean emitGlobal(String channel, WiredValue payload) {
        long[] state = globalRateState.computeIfAbsent(channel, k -> new long[]{System.currentTimeMillis(), 0});
        synchronized (state) {
            long now = System.currentTimeMillis();
            if (now - state[0] > GLOBAL_RATE_WINDOW_MS) {
                state[0] = now; state[1] = 0;
            }
            if (++state[1] > GLOBAL_RATE_LIMIT) return false;
        }
        for (List<SignalListener> ls : roomListeners.values()) {
            for (SignalListener l : ls) {
                try { l.onSignal(-1, channel, payload); }
                catch (Exception e) { log.warn("Global signal listener threw: channel={}", channel, e); }
            }
        }
        return true;
    }
}
