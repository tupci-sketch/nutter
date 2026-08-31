package com.habnut.emulator.game;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wobble Squabble: two players balanced on floats, each trying to put the other
 * in the water.
 *
 * Each player carries a balance that drifts away from centre on its own and is
 * pulled back by steadying. Shoving the opponent pushes their balance further
 * out; leaning in shoves harder but costs your own footing. Tip past the limit
 * and you fall, and the round goes to the other player.
 *
 * Balance is held here rather than on the client because it is the whole game:
 * a client that reported its own balance would simply never fall.
 */
public final class WobbleSquabbleMatch implements GameMatch {

    private static final AtomicLong ID_SEQ = new AtomicLong(1);

    private static final int TICK_RATE = 10;
    private static final int COUNTDOWN_TICKS = TICK_RATE * 3;

    /** Balance runs from -100 to 100; beyond that the player is in the water. */
    static final int BALANCE_LIMIT = 100;

    /** How far a plain shove moves the opponent. */
    static final int SHOVE_FORCE = 22;
    /** A leaning shove hits harder and costs the shover their own footing. */
    static final int LEAN_SHOVE_FORCE = 38;
    static final int LEAN_SELF_COST = 14;

    /** How much steadying pulls balance back toward centre. */
    static final int STEADY_RECOVERY = 18;

    /** Natural drift per tick, so standing still is not a strategy. */
    static final int DRIFT_PER_TICK = 2;

    /** Gap between actions. */
    static final int ACTION_COOLDOWN_TICKS = TICK_RATE / 2;

    /** Rounds needed to take the match. */
    static final int ROUNDS_TO_WIN = 3;

    static final class Player {
        final long userId;
        int balance;          // -100..100, 0 is steady
        int drift;            // which way balance is sliding
        int cooldown;
        int roundsWon;

        Player(long userId) {
            this.userId = userId;
            this.drift = 1;
        }

        boolean hasFallen() { return Math.abs(balance) >= BALANCE_LIMIT; }
    }

    private final long matchId;
    private final long roomId;

    private volatile GameState state = GameState.WAITING;
    private final Map<Long, Player> players = new ConcurrentHashMap<>();
    private final List<Long> order = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();

    private int countdown = 0;
    private int ticksElapsed = 0;
    private int round = 1;

    private GameObserver observer;

    public WobbleSquabbleMatch(long roomId) {
        this.matchId = ID_SEQ.getAndIncrement();
        this.roomId = roomId;
    }

    @Override public long      getMatchId()  { return matchId; }
    @Override public String    getGameType() { return "wobblesquabble"; }
    @Override public GameState getState()    { return state; }
    @Override public long      getRoomId()   { return roomId; }

    // ─── players ────────────────────────────────────────────────────────────

    /** Seats a player. A duel holds two; further joins are ignored. */
    @Override
    public void addPlayer(long userId, String team) {
        if (players.containsKey(userId) || players.size() >= 2) return;
        players.put(userId, new Player(userId));
        order.add(userId);
        scores.put(String.valueOf(userId), 0);
    }

    @Override
    public void removePlayer(long userId) {
        players.remove(userId);
        order.remove(userId);
        if (state == GameState.RUNNING) {
            // A duel with one player left is over.
            if (players.size() < 2) end();
        }
    }

    @Override public boolean    hasPlayer(long userId) { return players.containsKey(userId); }
    @Override public List<Long> getPlayers()           { return List.copyOf(players.keySet()); }

    /** The other player in the duel, if there is one. */
    private Player opponentOf(long userId) {
        for (Player p : players.values()) {
            if (p.userId != userId) return p;
        }
        return null;
    }

    // ─── lifecycle ──────────────────────────────────────────────────────────

    @Override
    public void start() {
        if (state != GameState.WAITING || players.size() < 2) return;
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
                    startRound();
                    notifyState();
                }
            }
            case RUNNING -> {
                ticksElapsed++;
                applyDrift();
                checkForFall();
            }
            default -> { }
        }
    }

    private void startRound() {
        for (Player p : players.values()) {
            p.balance = 0;
            p.cooldown = 0;
            // Each player drifts a different way, so neither starts favoured.
            p.drift = p.drift >= 0 ? 1 : -1;
        }
        emit("round_start", Map.of("round", round));
    }

    /** Balance slides a little every tick; standing still eventually tips you. */
    private void applyDrift() {
        for (Player p : players.values()) {
            if (p.cooldown > 0) p.cooldown--;
            p.balance += p.drift * DRIFT_PER_TICK;
            p.balance = clamp(p.balance);
        }
    }

    private void checkForFall() {
        for (Player p : players.values()) {
            if (!p.hasFallen()) continue;

            Player winner = opponentOf(p.userId);
            emit("player_fell", Map.of("userId", p.userId));

            if (winner != null) {
                winner.roundsWon++;
                scores.put(String.valueOf(winner.userId), winner.roundsWon);
                notifyScores();

                if (winner.roundsWon >= ROUNDS_TO_WIN) {
                    end();
                    return;
                }
            }

            round++;
            startRound();
            return;
        }
    }

    // ─── input ──────────────────────────────────────────────────────────────

    @Override
    public void onPlayerInput(long userId, String inputType, Map<String, Object> data) {
        if (state != GameState.RUNNING) return;
        Player player = players.get(userId);
        if (player == null || player.cooldown > 0 || player.hasFallen()) return;

        switch (inputType) {
            case "steady" -> handleSteady(player);
            case "shove"  -> handleShove(player, false);
            case "lean"   -> handleShove(player, true);
            default       -> { }
        }
    }

    /** Pulls balance back toward centre without overshooting past it. */
    private void handleSteady(Player player) {
        player.cooldown = ACTION_COOLDOWN_TICKS;

        if (player.balance > 0) {
            player.balance = Math.max(0, player.balance - STEADY_RECOVERY);
        } else if (player.balance < 0) {
            player.balance = Math.min(0, player.balance + STEADY_RECOVERY);
        }
        // Recovering flips which way you are about to slide.
        player.drift = -player.drift;

        emit("steadied", Map.of("userId", player.userId, "balance", player.balance));
    }

    /**
     * Shoves the opponent in whichever way they are already tipping, so a shove
     * compounds their trouble rather than cancelling it.
     */
    private void handleShove(Player player, boolean leaning) {
        Player target = opponentOf(player.userId);
        if (target == null || target.hasFallen()) return;

        player.cooldown = ACTION_COOLDOWN_TICKS;

        int force = leaning ? LEAN_SHOVE_FORCE : SHOVE_FORCE;
        int direction = target.balance >= 0 ? 1 : -1;
        target.balance = clamp(target.balance + force * direction);

        if (leaning) {
            // Leaning in costs your own footing.
            int selfDirection = player.balance >= 0 ? 1 : -1;
            player.balance = clamp(player.balance + LEAN_SELF_COST * selfDirection);
        }

        emit("shoved", Map.of(
            "userId", player.userId,
            "targetId", target.userId,
            "leaning", leaning,
            "targetBalance", target.balance,
            "balance", player.balance));
    }

    // ─── results ────────────────────────────────────────────────────────────

    @Override
    public Map<String, Integer> getScores() {
        return Map.copyOf(scores);
    }

    @Override
    public Map<String, Object> getResults() {
        long winner = players.values().stream()
            .max(Comparator.comparingInt(p -> p.roundsWon))
            .map(p -> p.userId)
            .orElse(-1L);

        Map<String, Integer> rounds = new HashMap<>();
        players.forEach((id, p) -> rounds.put(String.valueOf(id), p.roundsWon));

        return Map.of(
            "gameType", getGameType(),
            "winnerId", winner,
            "roundsWon", rounds,
            "rounds", round,
            "durationTicks", ticksElapsed);
    }

    @Override
    public void setObserver(GameObserver observer) {
        this.observer = observer;
    }

    // ─── inspection for tests ───────────────────────────────────────────────

    int balanceOf(long userId) {
        Player p = players.get(userId);
        return p == null ? 0 : p.balance;
    }

    int roundsWonBy(long userId) {
        Player p = players.get(userId);
        return p == null ? 0 : p.roundsWon;
    }

    int currentRound() { return round; }

    void beginPlay() {
        state = GameState.RUNNING;
        ticksElapsed = 0;
        startRound();
    }

    void setBalance(long userId, int balance) {
        Player p = players.get(userId);
        if (p != null) p.balance = clamp(balance);
    }

    void clearCooldowns() {
        players.values().forEach(p -> p.cooldown = 0);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private static int clamp(int balance) {
        return Math.max(-BALANCE_LIMIT, Math.min(BALANCE_LIMIT, balance));
    }

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
