package com.habnut.emulator.game;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lido Diving: take a turn on the board and score the dive.
 *
 * A diver climbs, picks a move, and times the release. The score comes from the
 * move's difficulty multiplied by how cleanly it was timed, so a hard dive taken
 * badly is worth less than an easy one taken well. Each diver gets a fixed
 * number of attempts and the best total wins.
 *
 * Timing is judged from when the server started the dive, not from anything the
 * client reports, so a diver cannot simply claim a perfect entry.
 */
public final class LidoDivingMatch implements GameMatch {

    private static final AtomicLong ID_SEQ = new AtomicLong(1);

    private static final int TICK_RATE = 10;
    private static final int COUNTDOWN_TICKS = TICK_RATE * 3;

    /** Attempts each diver gets. */
    static final int DIVES_PER_PLAYER = 3;

    /** How long a dive runs before it must be released, in ticks. */
    static final int DIVE_WINDOW_TICKS = TICK_RATE * 2;

    /** The tick within the window that scores a perfect entry. */
    static final int PERFECT_TICK = DIVE_WINDOW_TICKS / 2;

    /** Dives a player may attempt, with their difficulty. */
    public enum Dive {
        PENCIL(1.0),
        TUCK(1.4),
        PIKE(1.8),
        SOMERSAULT(2.2),
        SWAN(2.6);

        final double difficulty;

        Dive(double difficulty) {
            this.difficulty = difficulty;
        }
    }

    /** Base points a perfectly timed dive earns before difficulty. */
    static final int BASE_POINTS = 100;

    static final class Diver {
        final long userId;
        int divesTaken;
        int total;

        // The dive currently in the air, if any.
        Dive inFlight;
        int flightTicks;

        Diver(long userId) {
            this.userId = userId;
        }

        boolean isDiving() { return inFlight != null; }
        boolean isFinished() { return divesTaken >= DIVES_PER_PLAYER; }
    }

    private final long matchId;
    private final long roomId;

    private volatile GameState state = GameState.WAITING;
    private final Map<Long, Diver> divers = new ConcurrentHashMap<>();
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();

    private int countdown = 0;
    private int ticksElapsed = 0;

    private GameObserver observer;

    public LidoDivingMatch(long roomId) {
        this.matchId = ID_SEQ.getAndIncrement();
        this.roomId = roomId;
    }

    @Override public long      getMatchId()  { return matchId; }
    @Override public String    getGameType() { return "lidodiving"; }
    @Override public GameState getState()    { return state; }
    @Override public long      getRoomId()   { return roomId; }

    // ─── players ────────────────────────────────────────────────────────────

    @Override
    public void addPlayer(long userId, String team) {
        divers.putIfAbsent(userId, new Diver(userId));
        scores.putIfAbsent(String.valueOf(userId), 0);
    }

    @Override
    public void removePlayer(long userId) {
        divers.remove(userId);
        if (state == GameState.RUNNING && divers.isEmpty()) cancel();
    }

    @Override public boolean    hasPlayer(long userId) { return divers.containsKey(userId); }
    @Override public List<Long> getPlayers()           { return List.copyOf(divers.keySet()); }

    // ─── lifecycle ──────────────────────────────────────────────────────────

    @Override
    public void start() {
        if (state != GameState.WAITING || divers.isEmpty()) return;
        state = GameState.COUNTDOWN;
        countdown = COUNTDOWN_TICKS;
        notifyState();
    }

    @Override
    public void end() {
        if (state == GameState.ENDED) return;
        state = GameState.ENDED;
        notifyState();
        if (observer != null) observer.onMatchEnd(matchId, getResults());
    }

    @Override
    public void cancel() {
        if (state == GameState.ENDED || state == GameState.CANCELLED) return;
        state = GameState.CANCELLED;
        notifyState();
    }

    @Override
    public void onTick() {
        switch (state) {
            case COUNTDOWN -> {
                if (--countdown <= 0) {
                    state = GameState.RUNNING;
                    ticksElapsed = 0;
                    notifyState();
                }
            }
            case RUNNING -> {
                ticksElapsed++;
                advanceDives();
                if (allFinished()) end();
            }
            default -> { }
        }
    }

    /**
     * Moves dives through the air.
     *
     * A diver who never releases lands flat: the dive still counts as taken and
     * scores whatever the worst timing is worth, so waiting is not a way to
     * avoid a bad attempt.
     */
    private void advanceDives() {
        for (Diver diver : divers.values()) {
            if (!diver.isDiving()) continue;

            diver.flightTicks++;
            if (diver.flightTicks > DIVE_WINDOW_TICKS) {
                scoreDive(diver, diver.flightTicks);
            }
        }
    }

    private boolean allFinished() {
        if (divers.isEmpty()) return false;
        return divers.values().stream().allMatch(Diver::isFinished);
    }

    // ─── input ──────────────────────────────────────────────────────────────

    @Override
    public void onPlayerInput(long userId, String inputType, Map<String, Object> data) {
        if (state != GameState.RUNNING) return;
        Diver diver = divers.get(userId);
        if (diver == null) return;

        switch (inputType) {
            case "dive"    -> handleDive(diver, data);
            case "release" -> handleRelease(diver);
            default        -> { }
        }
    }

    /** Starts a dive, if the diver is on the board with attempts left. */
    private void handleDive(Diver diver, Map<String, Object> data) {
        if (diver.isDiving() || diver.isFinished()) return;

        Dive dive = parseDive(data.get("dive"));
        if (dive == null) return;

        diver.inFlight = dive;
        diver.flightTicks = 0;

        emit("dive_started", Map.of(
            "userId", diver.userId,
            "dive", dive.name(),
            "windowTicks", DIVE_WINDOW_TICKS));
    }

    /** Releases mid-dive; the closer to the middle of the window, the better. */
    private void handleRelease(Diver diver) {
        if (!diver.isDiving()) return;
        scoreDive(diver, diver.flightTicks);
    }

    /**
     * Scores and closes a dive.
     *
     * Timing is measured as distance from the perfect tick, so an early release
     * is penalised exactly as much as an equally late one.
     */
    private void scoreDive(Diver diver, int releasedAt) {
        Dive dive = diver.inFlight;
        if (dive == null) return;

        int offBy = Math.abs(releasedAt - PERFECT_TICK);
        double accuracy = Math.max(0.0, 1.0 - (double) offBy / PERFECT_TICK);
        int points = (int) Math.round(BASE_POINTS * dive.difficulty * accuracy);

        diver.inFlight = null;
        diver.flightTicks = 0;
        diver.divesTaken++;
        diver.total += points;
        scores.put(String.valueOf(diver.userId), diver.total);

        emit("dive_scored", Map.of(
            "userId", diver.userId,
            "dive", dive.name(),
            "points", points,
            "accuracy", Math.round(accuracy * 100),
            "divesTaken", diver.divesTaken));

        notifyScores();
    }

    private static Dive parseDive(Object raw) {
        if (raw == null) return null;
        try {
            return Dive.valueOf(raw.toString().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ─── results ────────────────────────────────────────────────────────────

    @Override
    public Map<String, Integer> getScores() {
        return Map.copyOf(scores);
    }

    @Override
    public Map<String, Object> getResults() {
        long winner = divers.values().stream()
            .max(Comparator.comparingInt(d -> d.total))
            .map(d -> d.userId)
            .orElse(-1L);

        return Map.of(
            "gameType", getGameType(),
            "winnerId", winner,
            "scores", Map.copyOf(scores),
            "durationTicks", ticksElapsed);
    }

    @Override
    public void setObserver(GameObserver observer) {
        this.observer = observer;
    }

    // ─── inspection for tests ───────────────────────────────────────────────

    int totalOf(long userId) {
        Diver d = divers.get(userId);
        return d == null ? 0 : d.total;
    }

    int divesTakenBy(long userId) {
        Diver d = divers.get(userId);
        return d == null ? 0 : d.divesTaken;
    }

    boolean isDiving(long userId) {
        Diver d = divers.get(userId);
        return d != null && d.isDiving();
    }

    void beginPlay() {
        state = GameState.RUNNING;
        ticksElapsed = 0;
    }

    /** Advances a dive in flight without waiting for real ticks. */
    void advanceFlight(long userId, int ticks) {
        Diver d = divers.get(userId);
        if (d != null && d.isDiving()) d.flightTicks += ticks;
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private void notifyState() {
        if (observer != null) observer.onStateChange(matchId, state);
    }

    private void notifyScores() {
        if (observer != null) observer.onScoreUpdate(matchId, getScores());
    }

    private void emit(String event, Map<String, Object> data) {
        if (observer != null) observer.onMatchEvent(matchId, event, data);
    }
}
