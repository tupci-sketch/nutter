package com.habnut.emulator.game;

import com.habnut.emulator.db.DatabaseManager;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.SessionRegistry;
import com.habnut.emulator.protocol.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GameEngine implements GameObserver {

    private static final Logger log = LoggerFactory.getLogger(GameEngine.class);

    private static final int TICK_RATE_HZ = 10;
    private static final long TICK_INTERVAL_MS = 1000L / TICK_RATE_HZ;

    private final DatabaseManager db;
    private final SessionRegistry sessionRegistry;
    private final PacketRouter    router;
    private final ConcurrentHashMap<Long, GameMatch> matches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long>      roomToMatch = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public GameEngine(DatabaseManager db, SessionRegistry sessionRegistry, PacketRouter router) {
        this.db              = db;
        this.sessionRegistry = sessionRegistry;
        this.router          = router;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> { Thread t = new Thread(r, "game-tick"); t.setDaemon(true); return t; });
        scheduler.scheduleAtFixedRate(this::tickAll, 0, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        log.info("GameEngine started at {} ticks/sec", TICK_RATE_HZ);
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (scheduler != null) scheduler.shutdownNow();
        log.info("GameEngine stopped");
    }

    // --- Match factory ---

    public GameMatch createMatch(String gameType, long roomId) {
        GameMatch match = switch (gameType) {
            case "football"   -> new FootballMatch(roomId);
            case "battleball" -> new BattleballMatch(roomId);
            case "freeze"     -> new FreezeMatch(roomId);
            case "racing"     -> new RacingMatch(roomId);
            case "telephrase" -> new TelephrasMatch(roomId);
            default -> throw new IllegalArgumentException("Unknown game type: " + gameType);
        };
        match.setObserver(this);
        matches.put(match.getMatchId(), match);
        roomToMatch.put(roomId, match.getMatchId());
        log.debug("Created {} match {} for room {}", gameType, match.getMatchId(), roomId);
        return match;
    }

    public Optional<GameMatch> getMatch(long matchId) {
        return Optional.ofNullable(matches.get(matchId));
    }

    public Optional<GameMatch> getMatchForRoom(long roomId) {
        Long matchId = roomToMatch.get(roomId);
        return matchId != null ? getMatch(matchId) : Optional.empty();
    }

    public void removeMatch(long matchId) {
        GameMatch m = matches.remove(matchId);
        if (m != null) roomToMatch.remove(m.getRoomId());
    }

    // --- Tick loop ---

    private void tickAll() {
        for (GameMatch match : matches.values()) {
            try {
                match.onTick();
            } catch (Exception e) {
                log.error("Error ticking match {}", match.getMatchId(), e);
            }
        }
    }

    // --- GameObserver callbacks ---

    @Override
    public void onStateChange(long matchId, GameState newState) {
        GameMatch match = matches.get(matchId);
        if (match == null) return;

        broadcastToMatch(match, PacketType.GAME_STATE_CHANGE,
            Map.of("matchId", matchId, "state", newState.name()));

        if (newState == GameState.ENDED || newState == GameState.CANCELLED) {
            matches.remove(matchId);
            roomToMatch.remove(match.getRoomId());
        }
    }

    @Override
    public void onScoreUpdate(long matchId, Map<String, Integer> scores) {
        GameMatch match = matches.get(matchId);
        if (match == null) return;
        broadcastToMatch(match, PacketType.GAME_SCORE_UPDATE,
            Map.of("matchId", matchId, "scores", scores));
    }

    @Override
    public void onMatchEvent(long matchId, String eventType, Map<String, Object> data) {
        GameMatch match = matches.get(matchId);
        if (match == null) return;
        Map<String, Object> payload = new HashMap<>(data);
        payload.put("matchId", matchId);
        payload.put("event", eventType);
        broadcastToMatch(match, PacketType.GAME_EVENT, payload);
    }

    @Override
    public void onMatchEnd(long matchId, Map<String, Object> results) {
        GameMatch match = matches.get(matchId);
        if (match == null) return;
        broadcastToMatch(match, PacketType.GAME_END,
            Map.of("matchId", matchId, "results", results));
        recordStats(match, results);
    }

    // --- Stats persistence ---

    private void recordStats(GameMatch match, Map<String, Object> results) {
        String winner = results.getOrDefault("winner", "draw").toString();
        @SuppressWarnings("unchecked")
        Map<String, Integer> scores = (Map<String, Integer>) results.getOrDefault("scores", Map.of());
        int duration = results.containsKey("duration") ? ((Number) results.get("duration")).intValue() : 0;

        try (Connection conn = db.getConnection()) {
            try (PreparedStatement matchStmt = conn.prepareStatement(
                "INSERT INTO habnut_game_stats (match_id, room_id, game_type, winner, duration_ticks, " +
                "red_score, blue_score, player_count, ended_at) " +
                "VALUES (?,?,?,?,?,?,?,?,NOW())")) {
                matchStmt.setLong(1, match.getMatchId());
                matchStmt.setLong(2, match.getRoomId());
                matchStmt.setString(3, match.getGameType());
                matchStmt.setString(4, winner);
                matchStmt.setInt(5, duration);
                matchStmt.setInt(6, scores.getOrDefault("red", 0));
                matchStmt.setInt(7, scores.getOrDefault("blue", 0));
                matchStmt.setInt(8, match.getPlayers().size());
                matchStmt.executeUpdate();
            }

            // Leaderboard: update each player's win/loss/play counts
            boolean gameOver = !"draw".equals(winner);
            for (long userId : match.getPlayers()) {
                String playerTeam = ""; // team resolved by match impl subclass
                boolean won = gameOver && !winner.isEmpty();
                try (PreparedStatement lb = conn.prepareStatement(
                    "INSERT INTO habnut_leaderboards (user_id, game_type, games_played, games_won) " +
                    "VALUES (?,?,1,?) ON DUPLICATE KEY UPDATE " +
                    "games_played = games_played + 1, games_won = games_won + ?")) {
                    lb.setLong(1, userId);
                    lb.setString(2, match.getGameType());
                    lb.setInt(3, won ? 1 : 0);
                    lb.setInt(4, won ? 1 : 0);
                    lb.executeUpdate();
                }
            }
        } catch (SQLException e) {
            log.error("Failed to record game stats for match {}", match.getMatchId(), e);
        }
    }

    // --- Broadcast helper ---

    private void broadcastToMatch(GameMatch match, String packetType, Map<String, Object> data) {
        String json = router.buildPacket(packetType, data);
        for (long userId : match.getPlayers()) {
            sessionRegistry.byUserId(userId).ifPresent(session -> session.send(json));
        }
    }
}
