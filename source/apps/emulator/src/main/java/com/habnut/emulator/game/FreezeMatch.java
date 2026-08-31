package com.habnut.emulator.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class FreezeMatch implements GameMatch {

    private static final Logger log = LoggerFactory.getLogger(FreezeMatch.class);
    private static final AtomicLong ID_SEQ = new AtomicLong(1);

    private static final int TICK_RATE          = 10;
    private static final int COUNTDOWN_TICKS    = TICK_RATE * 10;
    private static final int MAX_TICKS          = TICK_RATE * 60 * 3;
    private static final int FREEZE_TICKS       = TICK_RATE * 5;  // 5s frozen
    private static final int THAW_ASSIST_TICKS  = TICK_RATE * 3;  // 3s for ally to thaw
    private static final int SNOWBALL_RANGE     = 5;               // max throw tiles

    private final long matchId;
    private final long roomId;

    private volatile GameState state = GameState.WAITING;
    private final ConcurrentHashMap<Long, String>  playerTeams   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> frozenTicks   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, int[]>   positions     = new ConcurrentHashMap<>();
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();

    private int countdown    = 0;
    private int ticksElapsed = 0;

    private GameObserver observer;

    public FreezeMatch(long roomId) {
        this.matchId = ID_SEQ.getAndIncrement();
        this.roomId  = roomId;
        scores.put("red",  0);
        scores.put("blue", 0);
    }

    @Override public long      getMatchId()  { return matchId; }
    @Override public String    getGameType() { return "freeze"; }
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
        frozenTicks.remove(userId);
        positions.remove(userId);
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
        log.info("Freeze match {} ended: scores={}", matchId, scores);
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
                tickFrozenPlayers();
                ticksElapsed++;
                if (ticksElapsed >= MAX_TICKS) end();
            }
            default -> {}
        }
    }

    private void tickFrozenPlayers() {
        frozenTicks.replaceAll((uid, ticks) -> {
            int remaining = ticks - 1;
            if (remaining <= 0) {
                if (observer != null)
                    observer.onMatchEvent(matchId, "player_thawed", Map.of("userId", uid));
                return -1; // sentinel for removal
            }
            return remaining;
        });
        frozenTicks.values().removeIf(v -> v < 0);
    }

    @Override
    public void onPlayerInput(long userId, String inputType, Map<String, Object> data) {
        if (state != GameState.RUNNING) return;
        String myTeam = playerTeams.get(userId);
        if (myTeam == null) return;
        if (frozenTicks.containsKey(userId)) return; // frozen players can't act

        switch (inputType) {
            case "throw" -> handleThrow(userId, myTeam, data);
            case "thaw"  -> handleThaw(userId, myTeam, data);
            case "move"  -> handleMove(userId, data);
        }
    }

    private void handleThrow(long userId, String myTeam, Map<String, Object> data) {
        long targetId = toLong(data.get("targetId"));
        String targetTeam = playerTeams.get(targetId);
        if (targetTeam == null || targetTeam.equals(myTeam)) return;
        if (frozenTicks.containsKey(targetId)) return; // already frozen

        int[] myPos     = positions.get(userId);
        int[] targetPos = positions.get(targetId);
        if (myPos != null && targetPos != null) {
            double dist = Math.sqrt(Math.pow(myPos[0] - targetPos[0], 2) +
                                    Math.pow(myPos[1] - targetPos[1], 2));
            if (dist > SNOWBALL_RANGE) return;
        }

        frozenTicks.put(targetId, FREEZE_TICKS);
        scores.merge(myTeam, 1, Integer::sum);
        if (observer != null) {
            observer.onMatchEvent(matchId, "player_frozen",
                Map.of("throwerId", userId, "targetId", targetId));
            observer.onScoreUpdate(matchId, Map.copyOf(scores));
        }

        checkAllFrozen();
    }

    private void handleThaw(long userId, String myTeam, Map<String, Object> data) {
        long targetId = toLong(data.get("targetId"));
        String targetTeam = playerTeams.get(targetId);
        if (!myTeam.equals(targetTeam)) return;
        if (!frozenTicks.containsKey(targetId)) return;

        frozenTicks.remove(targetId);
        if (observer != null)
            observer.onMatchEvent(matchId, "player_thawed", Map.of("userId", targetId, "assistId", userId));
    }

    private void handleMove(long userId, Map<String, Object> data) {
        int x = toInt(data.get("x"));
        int y = toInt(data.get("y"));
        positions.put(userId, new int[]{x, y});
    }

    private void checkAllFrozen() {
        for (String team : List.of("red", "blue")) {
            boolean allFrozen = playerTeams.entrySet().stream()
                .filter(e -> team.equals(e.getValue()))
                .allMatch(e -> frozenTicks.containsKey(e.getKey()));
            if (allFrozen && playerTeams.values().stream().anyMatch(team::equals)) {
                if (observer != null)
                    observer.onMatchEvent(matchId, "team_all_frozen", Map.of("team", team));
                // Winning condition: all of opposing team frozen → check end
                end();
                return;
            }
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

    private long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v != null ? v.toString() : "0"); }
        catch (NumberFormatException e) { return 0; }
    }
}
