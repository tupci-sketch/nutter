package com.habnut.emulator.game;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Battle Ball: bounce across the floor and claim it for your team.
 *
 * A tile belongs to whoever bounced on it last. Bounce on your own tile again
 * and it hardens; a third bounce seals it, and a sealed tile cannot be taken.
 * The match ends when the floor is fully sealed or the clock runs out, and the
 * team holding the most tiles wins.
 *
 * Powerups sit on the floor and are picked up by bouncing over them. They are
 * placed by the server on a timer, so a client cannot conjure one.
 */
public final class BattleBallMatch implements GameMatch {

    private static final AtomicLong ID_SEQ = new AtomicLong(1);

    private static final int TICK_RATE = 10;
    private static final int COUNTDOWN_TICKS = TICK_RATE * 10;
    private static final int MATCH_TICKS = TICK_RATE * 60 * 3;

    /** Bounces needed to take, harden and finally seal a tile. */
    static final int BOUNCES_TO_SEAL = 3;

    static final int FIELD_W = 12;
    static final int FIELD_H = 12;

    /** Teams, in the order they are filled. */
    static final List<String> TEAMS = List.of("red", "blue", "green", "yellow");

    /** Points a tile is worth at each stage of being claimed. */
    static final int POINTS_CLAIM = 1;
    static final int POINTS_HARDEN = 1;
    static final int POINTS_SEAL = 2;

    /** How often a powerup appears, and how many may be on the floor at once. */
    static final int POWERUP_SPAWN_TICKS = TICK_RATE * 12;
    static final int MAX_POWERUPS = 4;

    /**
     * The powerups from the original game. Each is a one-shot effect applied
     * when a player bounces over it.
     */
    public enum Powerup {
        /** Seals every tile the team already holds around the player. */
        BATTLE_BOMB,
        /** Un-claims a ring of enemy tiles. */
        BOX_OF_PINS,
        /** Claims a straight line of tiles ahead. */
        CANNON,
        /** Reveals the whole floor state briefly. */
        FLASHLIGHT,
        /** Doubles points from the next few tiles. */
        LIGHTBULB,
        /** Seals a tile in one bounce. */
        POWER_DRILL,
        /** Lets the next bounce skip a tile. */
        SPRING,
    }

    /** A tile's owner and how far along it is to being sealed. */
    static final class Tile {
        String team;      // null when unclaimed
        int bounces;      // 0-3
        boolean sealed;
    }

    /** A powerup waiting on the floor. */
    record PlacedPowerup(int x, int y, Powerup type) { }

    static final class Player {
        final long userId;
        String team;
        int x, y;
        int doubleScoreTiles;  // remaining tiles scoring double, from a lightbulb
        boolean drillReady;    // next bounce seals outright
        boolean springReady;   // next bounce may skip a tile

        Player(long userId, String team) {
            this.userId = userId;
            this.team = team;
        }
    }

    private final long matchId;
    private final long roomId;

    private volatile GameState state = GameState.WAITING;
    private final Map<Long, Player> players = new ConcurrentHashMap<>();
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();
    private final Tile[][] tiles = new Tile[FIELD_H][FIELD_W];
    private final Map<String, PlacedPowerup> powerups = new ConcurrentHashMap<>();

    private int countdown = 0;
    private int ticksElapsed = 0;
    private int powerupTimer = 0;

    private GameObserver observer;

    public BattleBallMatch(long roomId) {
        this.matchId = ID_SEQ.getAndIncrement();
        this.roomId = roomId;
        for (String team : TEAMS) scores.put(team, 0);
        clearField();
    }

    private void clearField() {
        for (int y = 0; y < FIELD_H; y++) {
            for (int x = 0; x < FIELD_W; x++) {
                tiles[y][x] = new Tile();
            }
        }
        powerups.clear();
    }

    @Override public long      getMatchId()  { return matchId; }
    @Override public String    getGameType() { return "battleball"; }
    @Override public GameState getState()    { return state; }
    @Override public long      getRoomId()   { return roomId; }

    // ─── players ────────────────────────────────────────────────────────────

    @Override
    public void addPlayer(long userId, String team) {
        players.put(userId, new Player(userId, TEAMS.contains(team) ? team : smallestTeam()));
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

    @Override
    public void onTick() {
        switch (state) {
            case COUNTDOWN -> {
                if (--countdown <= 0) {
                    state = GameState.RUNNING;
                    ticksElapsed = 0;
                    powerupTimer = 0;
                    clearField();
                    for (String t : TEAMS) scores.put(t, 0);
                    notifyState();
                }
            }
            case RUNNING -> {
                if (++powerupTimer >= POWERUP_SPAWN_TICKS) {
                    powerupTimer = 0;
                    spawnPowerup();
                }
                if (++ticksElapsed >= MATCH_TICKS || fieldFullySealed()) end();
            }
            default -> { }
        }
    }

    // ─── input ──────────────────────────────────────────────────────────────

    @Override
    public void onPlayerInput(long userId, String inputType, Map<String, Object> data) {
        if (state != GameState.RUNNING) return;
        Player player = players.get(userId);
        if (player == null) return;

        if ("bounce".equals(inputType)) {
            handleBounce(player, intOf(data, "x", -1), intOf(data, "y", -1));
        }
    }

    /**
     * Resolves a bounce onto a tile.
     *
     * A bounce onto a sealed tile does nothing. A bounce onto an enemy tile
     * takes it back to the start for the new team, which is what makes an
     * unsealed tile worth defending.
     */
    void handleBounce(Player player, int x, int y) {
        if (!inBounds(x, y)) return;

        player.x = x;
        player.y = y;

        Tile tile = tiles[y][x];
        if (tile.sealed) return;

        if (player.drillReady) {
            // A drill seals outright, whoever held the tile before.
            player.drillReady = false;
            tile.team = player.team;
            tile.bounces = BOUNCES_TO_SEAL;
            tile.sealed = true;
            award(player, POINTS_SEAL);
            emit("tile_sealed", tileEvent(x, y, player));
        } else if (player.team.equals(tile.team)) {
            tile.bounces++;
            if (tile.bounces >= BOUNCES_TO_SEAL) {
                tile.sealed = true;
                award(player, POINTS_SEAL);
                emit("tile_sealed", tileEvent(x, y, player));
            } else {
                award(player, POINTS_HARDEN);
                emit("tile_hardened", tileEvent(x, y, player));
            }
        } else {
            // Taking a tile from another team resets its progress.
            tile.team = player.team;
            tile.bounces = 1;
            award(player, POINTS_CLAIM);
            emit("tile_claimed", tileEvent(x, y, player));
        }

        collectPowerup(player, x, y);
        notifyScores();
    }

    private Map<String, Object> tileEvent(int x, int y, Player player) {
        return Map.of(
            "x", x, "y", y,
            "team", player.team,
            "userId", player.userId,
            "bounces", tiles[y][x].bounces);
    }

    /** Adds points, doubling them while a lightbulb is active. */
    private void award(Player player, int points) {
        int total = points;
        if (player.doubleScoreTiles > 0) {
            player.doubleScoreTiles--;
            total *= 2;
        }
        scores.merge(player.team, total, Integer::sum);
    }

    // ─── powerups ───────────────────────────────────────────────────────────

    /** Places a powerup on a random unsealed tile, if the floor has room. */
    private void spawnPowerup() {
        if (powerups.size() >= MAX_POWERUPS) return;

        var rng = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 20; attempt++) {
            int x = rng.nextInt(FIELD_W);
            int y = rng.nextInt(FIELD_H);
            if (tiles[y][x].sealed || powerups.containsKey(key(x, y))) continue;

            Powerup type = Powerup.values()[rng.nextInt(Powerup.values().length)];
            powerups.put(key(x, y), new PlacedPowerup(x, y, type));
            emit("powerup_spawned", Map.of("x", x, "y", y, "type", type.name()));
            return;
        }
    }

    /** Applies and removes a powerup the player has just bounced onto. */
    private void collectPowerup(Player player, int x, int y) {
        PlacedPowerup placed = powerups.remove(key(x, y));
        if (placed == null) return;

        applyPowerup(player, placed.type(), x, y);
        emit("powerup_collected", Map.of(
            "userId", player.userId, "type", placed.type().name(), "x", x, "y", y));
    }

    void applyPowerup(Player player, Powerup type, int x, int y) {
        switch (type) {
            case BATTLE_BOMB -> {
                // Seals the player's own surrounding tiles.
                forEachNeighbour(x, y, (tx, ty) -> {
                    Tile t = tiles[ty][tx];
                    if (!t.sealed && player.team.equals(t.team)) {
                        t.sealed = true;
                        t.bounces = BOUNCES_TO_SEAL;
                        award(player, POINTS_SEAL);
                    }
                });
            }
            case BOX_OF_PINS -> {
                // Strips surrounding enemy tiles back to unclaimed.
                forEachNeighbour(x, y, (tx, ty) -> {
                    Tile t = tiles[ty][tx];
                    if (!t.sealed && t.team != null && !player.team.equals(t.team)) {
                        t.team = null;
                        t.bounces = 0;
                    }
                });
            }
            case CANNON -> {
                // Claims a short line running away from the player.
                for (int i = 1; i <= 3; i++) {
                    int tx = x + i;
                    if (!inBounds(tx, y)) break;
                    Tile t = tiles[y][tx];
                    if (t.sealed) continue;
                    t.team = player.team;
                    t.bounces = 1;
                    award(player, POINTS_CLAIM);
                }
            }
            case LIGHTBULB -> player.doubleScoreTiles += 5;
            case POWER_DRILL -> player.drillReady = true;
            case SPRING -> player.springReady = true;
            case FLASHLIGHT -> emit("field_revealed", Map.of("userId", player.userId));
        }
    }

    private interface TileVisitor { void visit(int x, int y); }

    private void forEachNeighbour(int x, int y, TileVisitor visitor) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int tx = x + dx;
                int ty = y + dy;
                if (inBounds(tx, ty)) visitor.visit(tx, ty);
            }
        }
    }

    // ─── results ────────────────────────────────────────────────────────────

    private boolean fieldFullySealed() {
        for (Tile[] row : tiles) {
            for (Tile t : row) {
                if (!t.sealed) return false;
            }
        }
        return true;
    }

    @Override
    public Map<String, Integer> getScores() {
        return Map.copyOf(scores);
    }

    @Override
    public Map<String, Object> getResults() {
        Map<String, Integer> held = new HashMap<>();
        for (String t : TEAMS) held.put(t, 0);
        for (Tile[] row : tiles) {
            for (Tile t : row) {
                if (t.team != null) held.merge(t.team, 1, Integer::sum);
            }
        }

        String winner = scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("none");

        return Map.of(
            "gameType", getGameType(),
            "winningTeam", winner,
            "teamScores", Map.copyOf(scores),
            "tilesHeld", held,
            "durationTicks", ticksElapsed);
    }

    @Override
    public void setObserver(GameObserver observer) {
        this.observer = observer;
    }

    // ─── inspection for tests ───────────────────────────────────────────────

    String tileOwner(int x, int y) { return inBounds(x, y) ? tiles[y][x].team : null; }
    boolean tileSealed(int x, int y) { return inBounds(x, y) && tiles[y][x].sealed; }
    int tileBounces(int x, int y) { return inBounds(x, y) ? tiles[y][x].bounces : 0; }
    Player playerOf(long userId) { return players.get(userId); }

    void beginPlay() {
        state = GameState.RUNNING;
        ticksElapsed = 0;
        clearField();
        for (String t : TEAMS) scores.put(t, 0);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private static boolean inBounds(int x, int y) {
        return x >= 0 && x < FIELD_W && y >= 0 && y < FIELD_H;
    }

    private static String key(int x, int y) { return x + ":" + y; }

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
}
