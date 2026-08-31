package com.habnut.emulator.net;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class RateLimiter {

    public enum Bucket { CHAT, MOVEMENT, FURNI, CATALOGUE_PURCHASE, COMBINED }

    private static final long CHAT_REFILL_NANOS       = 1_000_000_000L / 5;
    private static final long MOVEMENT_REFILL_NANOS   = 1_000_000_000L / 10;
    private static final long FURNI_REFILL_NANOS      = 1_000_000_000L / 20;
    private static final long CATALOGUE_REFILL_NANOS  = 1_000_000_000L / 5;
    private static final long COMBINED_REFILL_NANOS   = 1_000_000_000L / 100;

    private static final int CHAT_BURST       = 8;
    private static final int MOVEMENT_BURST   = 10;
    private static final int FURNI_BURST      = 20;
    private static final int CATALOGUE_BURST  = 5;
    private static final int COMBINED_BURST   = 100;

    private final ConcurrentHashMap<Long, long[]> tokenTimestamps = new ConcurrentHashMap<>();

    private static int bucketIndex(Bucket b) {
        return b.ordinal();
    }

    private static long refillNanos(Bucket b) {
        return switch (b) {
            case CHAT              -> CHAT_REFILL_NANOS;
            case MOVEMENT          -> MOVEMENT_REFILL_NANOS;
            case FURNI             -> FURNI_REFILL_NANOS;
            case CATALOGUE_PURCHASE-> CATALOGUE_REFILL_NANOS;
            case COMBINED          -> COMBINED_REFILL_NANOS;
        };
    }

    private static int burst(Bucket b) {
        return switch (b) {
            case CHAT              -> CHAT_BURST;
            case MOVEMENT          -> MOVEMENT_BURST;
            case FURNI             -> FURNI_BURST;
            case CATALOGUE_PURCHASE-> CATALOGUE_BURST;
            case COMBINED          -> COMBINED_BURST;
        };
    }

    public boolean tryAcquire(long sessionId, Bucket bucket) {
        long now = System.nanoTime();
        int buckets = Bucket.values().length;
        long[] state = tokenTimestamps.computeIfAbsent(sessionId, k -> {
            long[] arr = new long[buckets * 2];
            for (int i = 0; i < buckets; i++) {
                arr[i * 2]     = now;
                arr[i * 2 + 1] = burst(Bucket.values()[i]);
            }
            return arr;
        });

        synchronized (state) {
            int idx = bucketIndex(bucket) * 2;
            long lastRefill  = state[idx];
            long tokens      = state[idx + 1];
            long elapsed     = now - lastRefill;
            long refill      = refillNanos(bucket);
            long newTokens   = elapsed / refill;
            int  burstCap    = burst(bucket);

            if (newTokens > 0) {
                tokens = Math.min(burstCap, tokens + newTokens);
                state[idx] = lastRefill + newTokens * refill;
            }

            if (tokens <= 0) return false;

            state[idx + 1] = tokens - 1;
            return true;
        }
    }

    public void remove(long sessionId) {
        tokenTimestamps.remove(sessionId);
    }
}
