package com.habnut.emulator.trade;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class TradeSession {

    public enum State { OPEN, CONFIRMED_A, CONFIRMED_B, BOTH_CONFIRMED, COMPLETED, CANCELLED }

    public final long tradeId;
    public final long userIdA;
    public final long userIdB;

    private final Set<Long> offerA = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<Long> offerB = Collections.synchronizedSet(new LinkedHashSet<>());

    private volatile State state = State.OPEN;

    public TradeSession(long tradeId, long userIdA, long userIdB) {
        this.tradeId = tradeId;
        this.userIdA = userIdA;
        this.userIdB = userIdB;
    }

    public boolean addItem(long userId, long inventoryItemId) {
        if (state != State.OPEN) return false;
        if (userId == userIdA) return offerA.add(inventoryItemId);
        if (userId == userIdB) return offerB.add(inventoryItemId);
        return false;
    }

    public boolean removeItem(long userId, long inventoryItemId) {
        if (state != State.OPEN) return false;
        if (userId == userIdA) return offerA.remove(inventoryItemId);
        if (userId == userIdB) return offerB.remove(inventoryItemId);
        return false;
    }

    public boolean confirm(long userId) {
        if (userId == userIdA && state == State.OPEN) {
            state = State.CONFIRMED_A;
            return true;
        }
        if (userId == userIdB && state == State.OPEN) {
            state = State.CONFIRMED_B;
            return true;
        }
        if (userId == userIdA && state == State.CONFIRMED_B) {
            state = State.BOTH_CONFIRMED;
            return true;
        }
        if (userId == userIdB && state == State.CONFIRMED_A) {
            state = State.BOTH_CONFIRMED;
            return true;
        }
        return false;
    }

    public void unconfirm(long userId) {
        if (state == State.CONFIRMED_A || state == State.CONFIRMED_B || state == State.BOTH_CONFIRMED) {
            state = State.OPEN;
        }
    }

    public boolean cancel() {
        if (state == State.COMPLETED) return false;
        state = State.CANCELLED;
        return true;
    }

    public void complete() {
        state = State.COMPLETED;
    }

    public State getState()            { return state; }
    public Set<Long> getOfferA()       { return Collections.unmodifiableSet(offerA); }
    public Set<Long> getOfferB()       { return Collections.unmodifiableSet(offerB); }
    public boolean isReady()           { return state == State.BOTH_CONFIRMED; }
    public long partnerOf(long userId) { return userId == userIdA ? userIdB : userIdA; }
}
