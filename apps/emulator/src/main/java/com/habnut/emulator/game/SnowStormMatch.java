package com.habnut.emulator.game;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SnowStorm: a team snowball fight.
 *
 * Players carry a handful of snowballs, throw them at anyone not on their team,
 * and restock from a pile when they run dry. Five hits puts a player on the
 * ground for a few seconds; the thrower who lands the fifth scores the knockdown
 * on top of the hit.
 *
 * Everything is decided here. A client asks to throw at a target and the server
 * checks the thrower is standing, armed, off cooldown and in range, then decides
 * whether it lands. Nothing about ammunition, damage or score is taken from the
 * client, because a snowball fight is exactly the sort of thing players try to
 * script.
 */
public final class SnowStormMatch implements GameMatch {

    private static final AtomicLong ID_SEQ = new AtomicLong(1);

    /** Ticks per second, matching the engine's scheduler. */
    private static final int TICK_RATE = 10;

    private static final int COUNTDOWN_TICKS = TICK_RATE * 10;
    private static final int MATCH_TICKS     = TICK_RATE * 60 * 3;

    /** How many snowballs a player can hold at once. */
    static final int MAX_SNOWBALLS = 5;
    /** Hits a player absorbs before going down. */
    static final int HITS_TO_DOWN = 5;
    /** How long a downed player stays out of play. */
    static final int DOWN_TICKS = TICK_RATE * 5;
    /** Gap between throws, so a held mouse button is not an advantage. */
    static final int THROW_COOLDOWN_TICKS = TICK_RATE / 2;
    /** Ticks between one snowball being added back to a restocking player. */
    static final int RESTOCK_TICKS = TICK_RATE;

    /** Points for landing a snowball, and for the throw that downs someone. */
    static final int POINTS_PER_HIT = 1;
    static final int POINTS_PER_KNOCKDOWN = 5;

    /** How far a snowball carries, in tiles. */
    static final int THROW_RANGE = 8;

    /** Teams a player may be placed on. */
    static final List<String> TEAMS = List.of("red", "blue", "green", "yellow");

    /** One player's state for the duration of a match. */
    static final class Player {
        final long userId;
        String team;
        int snowballs = MAX_SNOWBALLS;
        int hitsTaken = 0;
        int downTicksRemaining = 0;
        int throwCooldown = 0;
        int restockTicks = 0;
        boolean restocking = false;
        int x, y;

        Player(long userId, String team) {
            this.userId = userId;
            this.team = team;
        }

        boolean isDown() { return downTicksRemaining > 0; }
    }

    private final long matchId;
    private final long roomId;

    private volatile GameState state = GameState.WAITING;
    private final Map<Long, Player> players = new ConcurrentHashMap<>();
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();
    private final Map<Long, Integer> personalScores = new ConcurrentHashMap<>();

    private int countdown = 0;
    private int ticksElapsed = 0;

    private GameObserver observer;

    public SnowStormMatch(long roomId) {
        this.matchId = ID_SEQ.getAndIncrement();
        this.roomId = roomId;
        for (String team : TEAMS) scores.put(team, 0);
    }

    @Override public long      getMatchId()  { return matchId; }
    @Override public String    getGameType() { return "snowstorm"; }
    @Override public GameState getState()    { return state; }
    @Override public long      getRoomId()   { return roomId; }

    // ─── players ────────────────────────────────────────────────────────────

    @Override
    public void addPlayer(long userId, String team) {
        players.put(userId, new Player(userId, normaliseTeam(team)));
        personalScores.putIfAbsent(userId, 0);
    }

    /** Places a player on the named team, or the smallest one if unnamed. */
    private String normaliseTeam(String team) {
        if (team != null && TEAMS.contains(team)) return team;
        return smallestTeam();
    }

    private String smallestTeam() {
        Map<String, Long> counts = new HashMap<>();
        for (String t : TEAMS) counts.put(t, 0L);
        for (Player p : players.values()) counts.merge(p.team, 1L, Long::sum);
        return counts.entrySet().stream()
            .min(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(TEAMS.get(0));
    }

    @Override
    public void removePlayer(long userId) {
        players.remove(userId);
        if (state == GameState.RUNNING && players.isEmpty()) cancel();
    }

    @Override public boolean    hasPlayer(long userId) { return players.containsKey(userId); }
    @Override public List<Long> getPlayers()           { return List.copyOf(players.keySet()); }

    /** Records where a player is standing, used for range checks. */
    public void updatePosition(long userId, int x, int y) {
        Player p = players.get(userId);
        if (p != null) {
            p.x = x;
            p.y = y;
        }
    }

    // ─── lifecycle ──────────────────────────────────────────────────────────

    @Override
    public void start() {
        if (state != GameState.WAITING) return;
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

    // ─── tick ───────────────────────────────────────────────────────────────

    @Override
    public void onTick() {
        switch (state) {
            case COUNTDOWN -> {
                if (--countdown <= 0) {
                    state = GameState.RUNNING;
                    ticksElapsed = 0;
                    resetPlayers();
                    notifyState();
                }
            }
            case RUNNING -> {
                tickPlayers();
                if (++ticksElapsed >= MATCH_TICKS) end();
            }
            default -> { }
        }
    }

    private void resetPlayers() {
        for (Player p : players.values()) {
            p.snowballs = MAX_SNOWBALLS;
            p.hitsTaken = 0;
            p.downTicksRemaining = 0;
            p.throwCooldown = 0;
            p.restocking = false;
            p.restockTicks = 0;
        }
    }

    /** Advances every per-player timer by one tick. */
    private void tickPlayers() {
        for (Player p : players.values()) {
            if (p.throwCooldown > 0) p.throwCooldown--;

            if (p.downTicksRemaining > 0 && --p.downTicksRemaining == 0) {
                // Back up with a clean slate and a full handful.
                p.hitsTaken = 0;
                p.snowballs = MAX_SNOWBALLS;
                emit("player_recovered", Map.of("userId", p.userId));
            }

            if (p.restocking && !p.isDown() && p.snowballs < MAX_SNOWBALLS
                && ++p.restockTicks >= RESTOCK_TICKS) {
                p.restockTicks = 0;
                p.snowballs++;
                emit("snowballs_changed", Map.of("userId", p.userId, "snowballs", p.snowballs));
                if (p.snowballs >= MAX_SNOWBALLS) p.restocking = false;
            }
        }
    }

    // ─── input ──────────────────────────────────────────────────────────────

    @Override
    public void onPlayerInput(long userId, String inputType, Map<String, Object> data) {
        if (state != GameState.RUNNING) return;
        Player player = players.get(userId);
        if (player == null) return;

        switch (inputType) {
            case "throw"    -> handleThrow(player, data);
            case "restock"  -> handleRestock(player, data);
            case "move"     -> updatePosition(userId, intOf(data, "x", player.x), intOf(data, "y", player.y));
            default         -> { }
        }
    }

    /**
     * Resolves a throw.
     *
     * The thrower must be up, armed and off cooldown, and the target must be a
     * living opponent within range. A throw that fails any of those is simply
     * not taken — the snowball is not spent.
     */
    private void handleThrow(Player thrower, Map<String, Object> data) {
        if (thrower.isDown() || thrower.throwCooldown > 0 || thrower.snowballs <= 0) return;

        long targetId = longOf(data, "targetUserId", -1);
        Player target = players.get(targetId);
        if (target == null || target.userId == thrower.userId) return;
        if (target.isDown()) return;
        if (target.team.equals(thrower.team)) return;
        if (distance(thrower, target) > THROW_RANGE) return;

        thrower.snowballs--;
        thrower.throwCooldown = THROW_COOLDOWN_TICKS;
        thrower.restocking = false;

        target.hitsTaken++;
        award(thrower, POINTS_PER_HIT);

        emit("snowball_hit", Map.of(
            "throwerId", thrower.userId,
            "targetId", target.userId,
            "hits", target.hitsTaken));
        emit("snowballs_changed", Map.of(
            "userId", thrower.userId, "snowballs", thrower.snowballs));

        if (target.hitsTaken >= HITS_TO_DOWN) {
            target.downTicksRemaining = DOWN_TICKS;
            target.snowballs = 0;
            award(thrower, POINTS_PER_KNOCKDOWN);
            emit("player_down", Map.of(
                "userId", target.userId,
                "byUserId", thrower.userId,
                "downTicks", DOWN_TICKS));
        }

        notifyScores();
    }

    /** Starts refilling. A downed player cannot scoop snow. */
    private void handleRestock(Player player, Map<String, Object> data) {
        if (player.isDown() || player.snowballs >= MAX_SNOWBALLS) return;
        player.restocking = true;
        player.restockTicks = 0;
        emit("restocking", Map.of("userId", player.userId));
    }

    private void award(Player player, int points) {
        scores.merge(player.team, points, Integer::sum);
        personalScores.merge(player.userId, points, Integer::sum);
    }

    /** Chebyshev distance: a throw reaches as far diagonally as it does straight. */
    private static int distance(Player a, Player b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    // ─── results ────────────────────────────────────────────────────────────

    @Override
    public Map<String, Integer> getScores() {
        return Map.copyOf(scores);
    }

    @Override
    public Map<String, Object> getResults() {
        String winner = scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("none");

        return Map.of(
            "gameType", getGameType(),
            "winningTeam", winner,
            "teamScores", Map.copyOf(scores),
            "playerScores", Map.copyOf(personalScores),
            "durationTicks", ticksElapsed);
    }

    @Override
    public void setObserver(GameObserver observer) {
        this.observer = observer;
    }

    // ─── inspection, used by the conformance and unit tests ─────────────────

    int snowballsOf(long userId) {
        Player p = players.get(userId);
        return p == null ? 0 : p.snowballs;
    }

    int hitsOn(long userId) {
        Player p = players.get(userId);
        return p == null ? 0 : p.hitsTaken;
    }

    boolean isDown(long userId) {
        Player p = players.get(userId);
        return p != null && p.isDown();
    }

    int personalScore(long userId) {
        return personalScores.getOrDefault(userId, 0);
    }

    String teamOf(long userId) {
        Player p = players.get(userId);
        return p == null ? null : p.team;
    }

    /** Drives the match straight into play, for tests and for a wired start. */
    void beginPlay() {
        state = GameState.RUNNING;
        ticksElapsed = 0;
        resetPlayers();
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

    private static int intOf(Map<String, Object> data, String key, int fallback) {
        Object v = data.get(key);
        return v instanceof Number n ? n.intValue() : fallback;
    }

    private static long longOf(Map<String, Object> data, String key, long fallback) {
        Object v = data.get(key);
        return v instanceof Number n ? n.longValue() : fallback;
    }
}
