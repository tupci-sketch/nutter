package com.habnut.emulator.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class BattleballMatch implements GameMatch {

    private static final Logger log = LoggerFactory.getLogger(BattleballMatch.class);
    private static final AtomicLong ID_SEQ = new AtomicLong(1);

    private static final int TICK_RATE       = 10;
    private static final int COUNTDOWN_TICKS = TICK_RATE * 10;
    private static final int MAX_TICKS       = TICK_RATE * 60 * 3; // 3 min

    // Tile states
    private static final int TILE_NEUTRAL = 0;
    private static final int TILE_RED     = 1;
    private static final int TILE_BLUE    = 2;
    private static final int TILE_SEALED_RED  = 3;
    private static final int TILE_SEALED_BLUE = 4;

    private static final int FIELD_W = 12;
    private static final int FIELD_H = 12;

    private final long matchId;
    private final long roomId;

    private volatile GameState state = GameState.WAITING;
    private final ConcurrentHashMap<Long, String> playerTeams = new ConcurrentHashMap<>();
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();

    // tile[y][x] = tile state
    private final int[][] tiles = new int[FIELD_H][FIELD_W];
    private int countdown   = 0;
    private int ticksElapsed = 0;

    private GameObserver observer;

    public BattleballMatch(long roomId) {
        this.matchId = ID_SEQ.getAndIncrement();
        this.roomId  = roomId;
        scores.put("red",  0);
        scores.put("blue", 0);
    }

    @Override public long      getMatchId()  { return matchId; }
    @Override public String    getGameType() { return "battleball"; }
    @Override public GameState getState()    { return state; }
    @Override public long      getRoomId()   { return roomId; }

    @Override
    public void addPlayer(long userId, String team) {
        String t = "red".equals(team) ? "red" : "blue";
        playerTeams.put(userId, t);
    }

    @Override
    public void removePlayer(long userId) {
        playerTeams.remove(userId);
        if (state == GameState.RUNNING && playerTeams.isEmpty()) cancel();
    }

    @Override public boolean    hasPlayer(long userId) { return playerTeams.containsKey(userId); }
    @Override public List<Long> getPlayers()           { return List.copyOf(playerTeams.keySet()); }

    @Override
    public void start() {
        if (state != GameState.WAITING) return;
        state = GameState.COUNTDOWN;
        countdown = COUNTDOWN_TICKS;
        if (observer != null) observer.onStateChange(matchId, GameState.COUNTDOWN);
        notifyCountdown(countdown / TICK_RATE);
    }

    @Override
    public void end() {
        state = GameState.ENDED;
        if (observer != null) {
            observer.onStateChange(matchId, GameState.ENDED);
            observer.onMatchEnd(matchId, getResults());
        }
        log.info("Battleball match {} ended: scores={}", matchId, scores);
    }

    @Override
    public void cancel() {
        state = GameState.CANCELLED;
        if (observer != null) observer.onStateChange(matchId, GameState.CANCELLED);
    }

    @Override
    public void onTick() {
        switch (state) {
            case COUNTDOWN -> {
                countdown--;
                if (countdown % TICK_RATE == 0 && countdown > 0) notifyCountdown(countdown / TICK_RATE);
                if (countdown <= 0) {
                    state = GameState.RUNNING;
                    ticksElapsed = 0;
                    if (observer != null) observer.onStateChange(matchId, GameState.RUNNING);
                }
            }
            case RUNNING -> {
                ticksElapsed++;
                if (ticksElapsed >= MAX_TICKS) end();
            }
            default -> {}
        }
    }

    @Override
    public void onPlayerInput(long userId, String inputType, Map<String, Object> data) {
        if (state != GameState.RUNNING) return;
        if (!"jump".equals(inputType)) return;
        String team = playerTeams.get(userId);
        if (team == null) return;

        int x = toInt(data.get("x"));
        int y = toInt(data.get("y"));
        if (x < 0 || x >= FIELD_W || y < 0 || y >= FIELD_H) return;

        int teamTile   = "red".equals(team) ? TILE_RED   : TILE_BLUE;
        int sealedTile = "red".equals(team) ? TILE_SEALED_RED : TILE_SEALED_BLUE;

        int current = tiles[y][x];
        if (current == sealedTile) return; // already sealed by own team

        if (current == teamTile) {
            // Second jump on own colour → seal
            tiles[y][x] = sealedTile;
            String other = "red".equals(team) ? "blue" : "red";
            checkSurrounded(other);
        } else {
            tiles[y][x] = teamTile;
        }

        broadcastTileChange(x, y, tiles[y][x]);
        recalcScores();
    }

    private void checkSurrounded(String enemyTeam) {
        int enemyTile   = "red".equals(enemyTeam) ? TILE_RED   : TILE_BLUE;
        int enemySealed = "red".equals(enemyTeam) ? TILE_SEALED_RED : TILE_SEALED_BLUE;
        int mySealed    = "red".equals(enemyTeam) ? TILE_SEALED_BLUE : TILE_SEALED_RED;

        boolean changed = true;
        while (changed) {
            changed = false;
            boolean[][] reachable = floodFillEdge(enemyTile, enemySealed);
            for (int y = 0; y < FIELD_H; y++) {
                for (int x = 0; x < FIELD_W; x++) {
                    int t = tiles[y][x];
                    if ((t == enemyTile || t == enemySealed) && !reachable[y][x]) {
                        tiles[y][x] = mySealed;
                        changed = true;
                    }
                }
            }
        }
    }

    private boolean[][] floodFillEdge(int tileA, int tileB) {
        boolean[][] visited = new boolean[FIELD_H][FIELD_W];
        Queue<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < FIELD_W; x++) {
            enqueueIfMatch(queue, visited, x, 0, tileA, tileB);
            enqueueIfMatch(queue, visited, x, FIELD_H - 1, tileA, tileB);
        }
        for (int y = 0; y < FIELD_H; y++) {
            enqueueIfMatch(queue, visited, 0, y, tileA, tileB);
            enqueueIfMatch(queue, visited, FIELD_W - 1, y, tileA, tileB);
        }
        int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int[] d : dirs) {
                int nx = p[0] + d[0], ny = p[1] + d[1];
                if (nx < 0 || nx >= FIELD_W || ny < 0 || ny >= FIELD_H) continue;
                enqueueIfMatch(queue, visited, nx, ny, tileA, tileB);
            }
        }
        return visited;
    }

    private void enqueueIfMatch(Queue<int[]> q, boolean[][] vis, int x, int y, int a, int b) {
        if (vis[y][x]) return;
        int t = tiles[y][x];
        if (t == a || t == b) { vis[y][x] = true; q.add(new int[]{x, y}); }
    }

    private void recalcScores() {
        int red = 0, blue = 0;
        for (int[] row : tiles) {
            for (int t : row) {
                if (t == TILE_SEALED_RED)  red++;
                if (t == TILE_SEALED_BLUE) blue++;
            }
        }
        scores.put("red",  red);
        scores.put("blue", blue);
        if (observer != null) observer.onScoreUpdate(matchId, Map.copyOf(scores));
    }

    private void broadcastTileChange(int x, int y, int tileState) {
        if (observer != null) {
            observer.onMatchEvent(matchId, "tile_changed",
                Map.of("x", x, "y", y, "state", tileState));
        }
    }

    private void notifyCountdown(int seconds) {
        if (observer != null) observer.onMatchEvent(matchId, "countdown", Map.of("seconds", seconds));
    }

    @Override public Map<String, Integer> getScores() { return Collections.unmodifiableMap(scores); }

    @Override
    public Map<String, Object> getResults() {
        int r = scores.getOrDefault("red", 0), b = scores.getOrDefault("blue", 0);
        String winner = r > b ? "red" : b > r ? "blue" : "draw";
        return Map.of("winner", winner, "scores", Map.copyOf(scores), "duration", ticksElapsed);
    }

    @Override public void setObserver(GameObserver observer) { this.observer = observer; }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v != null ? v.toString() : "0"); }
        catch (NumberFormatException e) { return 0; }
    }
}
