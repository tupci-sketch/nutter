package com.habnut.emulator.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Telephrase — players guess words from clue phrases whispered to them.
 * A host phrase is revealed one character at a time each tick.
 * First player to type the correct answer wins the round.
 * Configurable rounds; team or solo scoring.
 */
public final class TelephrasMatch implements GameMatch {

    private static final Logger log = LoggerFactory.getLogger(TelephrasMatch.class);
    private static final AtomicLong ID_SEQ = new AtomicLong(1);

    private static final int TICK_RATE          = 10;
    private static final int COUNTDOWN_TICKS    = TICK_RATE * 10;
    private static final int ROUND_TICKS        = TICK_RATE * 30; // 30s per round
    private static final int REVEAL_INTERVAL    = TICK_RATE * 4;  // reveal char every 4s
    private static final int TOTAL_ROUNDS       = 5;
    private static final int POINTS_PER_ROUND   = 10;
    private static final int HINT_PENALTY       = 2;              // deducted per revealed char

    private final long matchId;
    private final long roomId;

    private volatile GameState state = GameState.WAITING;
    private final ConcurrentHashMap<Long, String>  playerTeams = new ConcurrentHashMap<>();
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();

    private int countdown       = 0;
    private int ticksElapsed    = 0;
    private int currentRound    = 0;
    private int roundTicks      = 0;
    private int charsRevealed   = 0;
    private String currentPhrase = "";
    private String maskedPhrase  = "";
    private boolean roundAnswered = false;

    // Server-side phrase bank — populated at construction from GameEngine context
    private final List<String> phraseBank;

    private GameObserver observer;

    public TelephrasMatch(long roomId, List<String> phrases) {
        this.matchId    = ID_SEQ.getAndIncrement();
        this.roomId     = roomId;
        this.phraseBank = new ArrayList<>(phrases.isEmpty() ? DEFAULT_PHRASES : phrases);
        Collections.shuffle(this.phraseBank);
        scores.put("red",  0);
        scores.put("blue", 0);
    }

    public TelephrasMatch(long roomId) {
        this(roomId, Collections.emptyList());
    }

    @Override public long      getMatchId()  { return matchId; }
    @Override public String    getGameType() { return "telephrase"; }
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
        log.info("Telephrase match {} ended after {} rounds: scores={}", matchId, currentRound, scores);
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
                    startNextRound();
                    if (observer != null) observer.onStateChange(matchId, GameState.RUNNING);
                }
            }
            case RUNNING -> {
                if (!roundAnswered) {
                    roundTicks++;
                    // Reveal next character at interval
                    if (roundTicks % REVEAL_INTERVAL == 0) revealNextChar();
                    if (roundTicks >= ROUND_TICKS) roundTimeout();
                }
                ticksElapsed++;
            }
            default -> {}
        }
    }

    @Override
    public void onPlayerInput(long userId, String inputType, Map<String, Object> data) {
        if (state != GameState.RUNNING) return;
        if (!"answer".equals(inputType)) return;
        if (roundAnswered) return;
        String team = playerTeams.get(userId);
        if (team == null) return;

        String answer = data.get("text") != null ? data.get("text").toString().trim() : "";
        if (answer.equalsIgnoreCase(currentPhrase)) {
            roundAnswered = true;
            int points = Math.max(1, POINTS_PER_ROUND - charsRevealed * HINT_PENALTY);
            scores.merge(team, points, Integer::sum);
            if (observer != null) {
                observer.onMatchEvent(matchId, "round_won", Map.of(
                    "userId", userId, "team", team, "answer", currentPhrase, "points", points));
                observer.onScoreUpdate(matchId, Map.copyOf(scores));
            }
            scheduleNextRound();
        }
    }

    private void startNextRound() {
        if (currentRound >= TOTAL_ROUNDS || phraseBank.isEmpty()) { end(); return; }
        currentPhrase = phraseBank.remove(0);
        maskedPhrase  = "_".repeat(currentPhrase.length());
        charsRevealed = 0;
        roundTicks    = 0;
        roundAnswered = false;
        currentRound++;
        if (observer != null) {
            observer.onMatchEvent(matchId, "round_start", Map.of(
                "round", currentRound, "totalRounds", TOTAL_ROUNDS,
                "length", currentPhrase.length(), "masked", maskedPhrase));
        }
    }

    private void revealNextChar() {
        if (charsRevealed >= currentPhrase.length()) return;
        // Reveal a random unrevealed position
        List<Integer> hidden = new ArrayList<>();
        for (int i = 0; i < currentPhrase.length(); i++) {
            if (maskedPhrase.charAt(i) == '_') hidden.add(i);
        }
        if (hidden.isEmpty()) return;
        int idx = hidden.get((int)(Math.random() * hidden.size()));
        char[] m = maskedPhrase.toCharArray();
        m[idx] = currentPhrase.charAt(idx);
        maskedPhrase = new String(m);
        charsRevealed++;
        if (observer != null)
            observer.onMatchEvent(matchId, "char_revealed",
                Map.of("masked", maskedPhrase, "position", idx));
    }

    private void roundTimeout() {
        roundAnswered = true;
        if (observer != null)
            observer.onMatchEvent(matchId, "round_timeout",
                Map.of("answer", currentPhrase, "round", currentRound));
        scheduleNextRound();
    }

    private void scheduleNextRound() {
        // Brief inter-round pause handled by resetting roundTicks to -TICK_RATE (1s pause)
        roundTicks = -TICK_RATE;
        startNextRound();
    }

    private void notifyCountdown(int seconds) {
        if (observer != null) observer.onMatchEvent(matchId, "countdown", Map.of("seconds", seconds));
    }

    @Override public Map<String, Integer> getScores() { return Collections.unmodifiableMap(scores); }

    @Override
    public Map<String, Object> getResults() {
        int r = scores.getOrDefault("red", 0), b = scores.getOrDefault("blue", 0);
        String winner = r > b ? "red" : b > r ? "blue" : "draw";
        return Map.of("winner", winner, "scores", Map.copyOf(scores),
            "rounds", currentRound, "duration", ticksElapsed);
    }

    @Override public void setObserver(GameObserver observer) { this.observer = observer; }

    private static final List<String> DEFAULT_PHRASES = List.of(
        "sunflower", "telescope", "umbrella", "caterpillar", "lighthouse",
        "waterfall", "butterfly", "pineapple", "sandcastle", "jellyfish",
        "thunderstorm", "moonlight", "fireplace", "dragonfly", "snowflake",
        "starfish", "honeybee", "rainbow", "volcano", "blizzard"
    );
}
