package com.habnut.emulator.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class RacingMatch implements GameMatch {

    private static final Logger log = LoggerFactory.getLogger(RacingMatch.class);
    private static final AtomicLong ID_SEQ = new AtomicLong(1);

    private static final int TICK_RATE       = 10;
    private static final int COUNTDOWN_TICKS = TICK_RATE * 10;
    private static final int MAX_TICKS       = TICK_RATE * 60 * 3;
    private static final int TOTAL_LAPS      = 3;
    private static final int TOTAL_WAYPOINTS = 8;

    private final long matchId;
    private final long roomId;

    private volatile GameState state = GameState.WAITING;
    private final ConcurrentHashMap<Long, String>  playerTeams    = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> currentLap     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> currentWaypoint = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> finishPosition = new ConcurrentHashMap<>();
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();

    private int nextFinishPos = 1;
    private int countdown    = 0;
    private int ticksElapsed = 0;

    private GameObserver observer;

    public RacingMatch(long roomId) {
        this.matchId = ID_SEQ.getAndIncrement();
        this.roomId  = roomId;
        scores.put("red",  0);
        scores.put("blue", 0);
    }

    @Override public long      getMatchId()  { return matchId; }
    @Override public String    getGameType() { return "racing"; }
    @Override public GameState getState()    { return state; }
    @Override public long      getRoomId()   { return roomId; }

    @Override
    public void addPlayer(long userId, String team) {
        String t = "red".equals(team) ? "red" : "blue";
        playerTeams.put(userId, t);
        currentLap.put(userId, 0);
        currentWaypoint.put(userId, 0);
    }

    @Override
    public void removePlayer(long userId) {
        playerTeams.remove(userId);
        currentLap.remove(userId);
        currentWaypoint.remove(userId);
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
        log.info("Racing match {} ended: scores={}", matchId, scores);
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
        if (!"waypoint".equals(inputType)) return;
        String team = playerTeams.get(userId);
        if (team == null) return;
        if (finishPosition.containsKey(userId)) return; // already finished

        int claimedWaypoint = toInt(data.get("waypoint"));
        int playerWp  = currentWaypoint.getOrDefault(userId, 0);
        int playerLap = currentLap.getOrDefault(userId, 0);

        // Must arrive at next sequential waypoint
        if (claimedWaypoint != (playerWp + 1) % TOTAL_WAYPOINTS) return;

        int nextWp = claimedWaypoint;
        currentWaypoint.put(userId, nextWp);

        if (nextWp == 0) {
            // Completed a lap (wrapped around)
            int lap = playerLap + 1;
            currentLap.put(userId, lap);

            if (observer != null)
                observer.onMatchEvent(matchId, "lap_completed",
                    Map.of("userId", userId, "lap", lap, "totalLaps", TOTAL_LAPS));

            if (lap >= TOTAL_LAPS) {
                // Finished the race
                int pos = nextFinishPos++;
                finishPosition.put(userId, pos);
                int points = Math.max(0, playerTeams.size() - pos + 1);
                scores.merge(team, points, Integer::sum);
                if (observer != null) {
                    observer.onMatchEvent(matchId, "player_finished",
                        Map.of("userId", userId, "position", pos, "team", team));
                    observer.onScoreUpdate(matchId, Map.copyOf(scores));
                }
                if (finishPosition.size() >= playerTeams.size()) end();
            }
        } else {
            if (observer != null)
                observer.onMatchEvent(matchId, "waypoint_reached",
                    Map.of("userId", userId, "waypoint", nextWp));
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
        return Map.of("winner", winner, "scores", Map.copyOf(scores), "duration", ticksElapsed,
            "finishPositions", Map.copyOf(finishPosition));
    }

    @Override public void setObserver(GameObserver observer) { this.observer = observer; }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v != null ? v.toString() : "0"); }
        catch (NumberFormatException e) { return 0; }
    }
}
