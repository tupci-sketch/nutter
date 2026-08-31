package com.habnut.emulator.trade;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class TradeService {

    private static final Logger log = LoggerFactory.getLogger(TradeService.class);

    private final DatabaseManager db;
    private final ConcurrentHashMap<Long, TradeSession> activeTrades  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> userToTrade = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    public TradeService(DatabaseManager db) {
        this.db = db;
    }

    public TradeSession open(long userIdA, long userIdB) {
        if (userToTrade.containsKey(userIdA) || userToTrade.containsKey(userIdB)) {
            throw new IllegalStateException("One or both users are already trading");
        }
        long id = idSeq.getAndIncrement();
        TradeSession session = new TradeSession(id, userIdA, userIdB);
        activeTrades.put(id, session);
        userToTrade.put(userIdA, id);
        userToTrade.put(userIdB, id);
        return session;
    }

    public Optional<TradeSession> getForUser(long userId) {
        Long id = userToTrade.get(userId);
        return id == null ? Optional.empty() : Optional.ofNullable(activeTrades.get(id));
    }

    public void complete(TradeSession session) throws SQLException {
        // Atomic inventory swap — move items A→B and B→A
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            for (long itemId : session.getOfferA()) {
                try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE habnut_items_inventory SET owner_id = ? WHERE id = ? AND owner_id = ?")) {
                    ps.setLong(1, session.userIdB); ps.setLong(2, itemId); ps.setLong(3, session.userIdA);
                    if (ps.executeUpdate() == 0) { conn.rollback(); throw new IllegalStateException("Item not owned by A: " + itemId); }
                }
            }
            for (long itemId : session.getOfferB()) {
                try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE habnut_items_inventory SET owner_id = ? WHERE id = ? AND owner_id = ?")) {
                    ps.setLong(1, session.userIdA); ps.setLong(2, itemId); ps.setLong(3, session.userIdB);
                    if (ps.executeUpdate() == 0) { conn.rollback(); throw new IllegalStateException("Item not owned by B: " + itemId); }
                }
            }
            conn.commit();
        }
        session.complete();
        close(session);
        log.info("Trade {} completed: A={} B={}", session.tradeId, session.userIdA, session.userIdB);
    }

    public void cancel(TradeSession session) {
        session.cancel();
        close(session);
    }

    private void close(TradeSession session) {
        activeTrades.remove(session.tradeId);
        userToTrade.remove(session.userIdA);
        userToTrade.remove(session.userIdB);
    }
}
