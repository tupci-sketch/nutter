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
            case "football"       -> new FootballMatch(roomId);
            case "battleball"     -> new BattleBallMatch(roomId);
            case "freeze"         -> new FreezeMatch(roomId);
            case "racing"         -> new RacingMatch(roomId);
            case "telephrase"     -> new TelephrasMatch(roomId);
            case "snowstorm"      -> new SnowStormMatch(roomId);
            case "wobblesquabble" -> new WobbleSquabbleMatch(roomId);
            case "lidodiving"     -> new LidoDivingMatch(roomId);
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

    /**
     * Writes a finished match to the record.
     *
     * Both tables are per-player: a leaderboard row is one player's standing at
     * one game, and a stats row is one player's part in one match. Writing a
     * match-shaped row into either produces nothing — every repository here
     * catches its own exception and carries on, so a mismatch is silent, and
     * the only symptom is a leaderboard that stays empty forever.
     */
    private void recordStats(GameMatch match, Map<String, Object> results) {
        String winner = results.getOrDefault("winner", "draw").toString();
        @SuppressWarnings("unchecked")
        Map<String, Integer> scores = (Map<String, Integer>) results.getOrDefault("scores", Map.of());
        long durationMs = results.containsKey("durationTicks")
            ? ((Number) results.get("durationTicks")).longValue() * TICK_INTERVAL_MS
            : 0L;
        boolean drawn = "draw".equals(winner);

        try (Connection conn = db.getConnection()) {
            for (long userId : match.getPlayers()) {
                int points = scores.getOrDefault(String.valueOf(userId), 0);
                boolean won = !drawn && winner.equals(String.valueOf(userId));

                recordPlayerMatch(conn, match, userId, points, won, durationMs);
                updateLeaderboard(conn, match.getGameType(), userId, points, won);
            }
        } catch (SQLException e) {
            log.error("Failed to record game stats for match {}", match.getMatchId(), e);
        }
    }

    /** One player's part in one match. */
    private void recordPlayerMatch(Connection conn, GameMatch match, long userId,
                                   int points, boolean won, long durationMs) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO habnut_game_stats "
            + "(user_id, match_id, game_type, score, won, duration_ms, recorded_at) "
            + "VALUES (?,?,?,?,?,?,NOW())")) {
            ps.setLong(1, userId);
            ps.setString(2, String.valueOf(match.getMatchId()));
            ps.setString(3, match.getGameType());
            ps.setInt(4, points);
            ps.setBoolean(5, won);
            ps.setLong(6, durationMs);
            ps.executeUpdate();
        }
    }

    /**
     * One player's running standing at one game.
     *
     * The period is named rather than left to a default, because a row is
     * unique per player, game and period: leaving it out would make an all-time
     * record and a weekly one the same row.
     */
    private void updateLeaderboard(Connection conn, String gameType, long userId,
                                   int points, boolean won) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO habnut_leaderboards "
            + "(user_id, game_type, period, matches_played, wins, total_score) "
            + "VALUES (?,?,'all_time',1,?,?) ON DUPLICATE KEY UPDATE "
            + "matches_played = matches_played + 1, wins = wins + ?, "
            + "total_score = total_score + ?")) {
            ps.setLong(1, userId);
            ps.setString(2, gameType);
            ps.setInt(3, won ? 1 : 0);
            ps.setInt(4, points);
            ps.setInt(5, won ? 1 : 0);
            ps.setInt(6, points);
            ps.executeUpdate();
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
