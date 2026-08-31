package com.habnut.emulator.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class FootballMatch implements GameMatch {

    private static final Logger log = LoggerFactory.getLogger(FootballMatch.class);
    private static final AtomicLong ID_SEQ = new AtomicLong(1);

    // Field dimensions (tile-based; configurable per room)
    private static final double FIELD_MIN_X = 1;
    private static final double FIELD_MIN_Y = 1;
    private static final double FIELD_MAX_X = 18;
    private static final double FIELD_MAX_Y = 12;
    private static final double CENTER_X    = (FIELD_MIN_X + FIELD_MAX_X) / 2;
    private static final double CENTER_Y    = (FIELD_MIN_Y + FIELD_MAX_Y) / 2;

    // Goal areas: left team scores in right goal, right team in left goal
    private static final double GOAL_L_X     = FIELD_MIN_X - 1;
    private static final double GOAL_R_X     = FIELD_MAX_X + 1;
    private static final double GOAL_Y_MIN   = 5;
    private static final double GOAL_Y_MAX   = 7;

    private static final int TICK_RATE        = 10; // 10 ticks/sec
    private static final int COUNTDOWN_TICKS  = TICK_RATE * 10; // 10s countdown
    private static final int MAX_DURATION_TICKS = TICK_RATE * 60 * 5; // 5 min
    private static final int KICK_RANGE_TILES  = 2;

    private final long matchId;
    private final long roomId;

    private volatile GameState state = GameState.WAITING;
    private final ConcurrentHashMap<Long, String> playerTeams = new ConcurrentHashMap<>();
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();
    private final Ball ball;
    private int countdown  = 0;
    private int ticksElapsed = 0;

    private GameObserver observer;

    public FootballMatch(long roomId) {
        this.matchId = ID_SEQ.getAndIncrement();
        this.roomId  = roomId;
        this.ball    = new Ball(CENTER_X, CENTER_Y);
        scores.put("red",  0);
        scores.put("blue", 0);
    }

    @Override public long   getMatchId()  { return matchId; }
    @Override public String getGameType() { return "football"; }
    @Override public GameState getState() { return state; }
    @Override public long   getRoomId()   { return roomId; }

    @Override
    public void addPlayer(long userId, String team) {
        String t = "red".equals(team) ? "red" : "blue";
        playerTeams.put(userId, t);
        log.debug("Football match {}: player {} joined team {}", matchId, userId, t);
    }

    @Override
    public void removePlayer(long userId) {
        playerTeams.remove(userId);
        if (state == GameState.RUNNING && playerTeams.isEmpty()) cancel();
    }

    @Override public boolean     hasPlayer(long userId) { return playerTeams.containsKey(userId); }
    @Override public List<Long>  getPlayers()           { return List.copyOf(playerTeams.keySet()); }

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
        recordStats();
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
                if (countdown % TICK_RATE == 0 && countdown > 0) {
                    notifyCountdown(countdown / TICK_RATE);
                }
                if (countdown <= 0) {
                    state = GameState.RUNNING;
                    ticksElapsed = 0;
                    if (observer != null) observer.onStateChange(matchId, GameState.RUNNING);
                }
            }
            case RUNNING -> {
                ball.tick(FIELD_MIN_X, FIELD_MIN_Y, FIELD_MAX_X, FIELD_MAX_Y);

                if (ball.isMoving()) {
                    if (observer != null) {
                        observer.onMatchEvent(matchId, "ball_moved", Map.of(
                            "x", ball.getX(), "y", ball.getY(),
                            "vx", ball.getVx(), "vy", ball.getVy()));
                    }
                }

                checkGoal();
                ticksElapsed++;
                if (ticksElapsed >= MAX_DURATION_TICKS) end();
            }
            default -> {}
        }
    }

    private void checkGoal() {
        double bx = ball.getX(); double by = ball.getY();
        if (by < GOAL_Y_MIN || by > GOAL_Y_MAX) return;

        if (bx <= GOAL_L_X) {
            // Ball in left goal → blue scores
            scores.merge("blue", 1, Integer::sum);
            announceGoal("blue");
        } else if (bx >= GOAL_R_X) {
            // Ball in right goal → red scores
            scores.merge("red", 1, Integer::sum);
            announceGoal("red");
        }
    }

    private void announceGoal(String scoringTeam) {
        ball.reset(CENTER_X, CENTER_Y);
        if (observer != null) {
            observer.onMatchEvent(matchId, "goal",
                Map.of("team", scoringTeam, "scores", Map.copyOf(scores)));
            observer.onScoreUpdate(matchId, Map.copyOf(scores));
        }
    }

    @Override
    public void onPlayerInput(long userId, String inputType, Map<String, Object> data) {
        if (state != GameState.RUNNING) return;
        if (!"kick".equals(inputType)) return;
        if (!playerTeams.containsKey(userId)) return;

        double playerX = toDouble(data.get("playerX"));
        double playerY = toDouble(data.get("playerY"));
        double targetX = toDouble(data.get("targetX"));
        double targetY = toDouble(data.get("targetY"));

        // Validate player is within kick range of ball
        double distToBall = Math.sqrt(
            Math.pow(playerX - ball.getX(), 2) + Math.pow(playerY - ball.getY(), 2));
        if (distToBall > KICK_RANGE_TILES) return;

        ball.kick(userId, playerX, playerY, targetX, targetY);
        if (observer != null) {
            observer.onMatchEvent(matchId, "ball_kicked", Map.of(
                "kickerId", userId, "x", ball.getX(), "y", ball.getY(),
                "vx", ball.getVx(), "vy", ball.getVy()));
        }
    }

    @Override public Map<String, Integer> getScores() { return Collections.unmodifiableMap(scores); }

    @Override
    public Map<String, Object> getResults() {
        int redScore  = scores.getOrDefault("red", 0);
        int blueScore = scores.getOrDefault("blue", 0);
        String winner = redScore > blueScore ? "red" : blueScore > redScore ? "blue" : "draw";
        return Map.of("winner", winner, "scores", Map.copyOf(scores), "duration", ticksElapsed);
    }

    @Override public void setObserver(GameObserver observer) { this.observer = observer; }

    private void notifyCountdown(int seconds) {
        if (observer != null)
            observer.onMatchEvent(matchId, "countdown", Map.of("seconds", seconds));
    }

    private void recordStats() {
        // Stats recording handled by GameEngine → habnut_game_stats
        log.info("Football match {} ended: scores={}", matchId, scores);
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v != null ? v.toString() : "0"); }
        catch (NumberFormatException e) { return 0; }
    }
}
