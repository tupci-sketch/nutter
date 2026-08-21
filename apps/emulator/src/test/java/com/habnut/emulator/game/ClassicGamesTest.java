package com.habnut.emulator.game;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The classic games.
 *
 * Each of these is server-authoritative, so the tests push the same input a
 * client would and check the server's own state rather than trusting anything
 * reported back. The cases that matter are the ones a player would try to
 * exploit: throwing with no ammunition, hitting a team-mate, scoring a tile
 * someone else sealed, or claiming a perfect dive without timing it.
 */
class ClassicGamesTest {

    /** Collects everything a match emits so tests can assert on it. */
    static final class RecordingObserver implements GameObserver {
        final List<GameState> states = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        final List<Map<String, Object>> eventData = new ArrayList<>();
        Map<String, Object> results;

        @Override public void onStateChange(long matchId, GameState newState) { states.add(newState); }
        @Override public void onScoreUpdate(long matchId, Map<String, Integer> scores) { }

        @Override
        public void onMatchEvent(long matchId, String eventType, Map<String, Object> data) {
            events.add(eventType);
            eventData.add(data);
        }

        @Override public void onMatchEnd(long matchId, Map<String, Object> r) { results = r; }

        boolean sawEvent(String name) { return events.contains(name); }
        long countEvent(String name) { return events.stream().filter(name::equals).count(); }
    }

    // ─── SnowStorm ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SnowStorm")
    class SnowStorm {

        private SnowStormMatch match;
        private RecordingObserver observer;

        private static final long THROWER = 1L;
        private static final long TARGET = 2L;

        @BeforeEach
        void setUp() {
            match = new SnowStormMatch(100L);
            observer = new RecordingObserver();
            match.setObserver(observer);

            match.addPlayer(THROWER, "red");
            match.addPlayer(TARGET, "blue");
            match.updatePosition(THROWER, 0, 0);
            match.updatePosition(TARGET, 1, 0);
            match.beginPlay();
        }

        /** Throws once, clearing the cooldown so a test can throw again. */
        private void throwAt(long thrower, long target) {
            match.onPlayerInput(thrower, "throw", Map.of("targetUserId", target));
            match.onTick(); // burn a tick so the cooldown expires
            for (int i = 0; i < SnowStormMatch.THROW_COOLDOWN_TICKS; i++) match.onTick();
        }

        @Test
        @DisplayName("players start with a full handful")
        void startsArmed() {
            assertEquals(SnowStormMatch.MAX_SNOWBALLS, match.snowballsOf(THROWER));
        }

        @Test
        @DisplayName("a hit costs a snowball, lands a hit and scores a point")
        void hitScores() {
            match.onPlayerInput(THROWER, "throw", Map.of("targetUserId", TARGET));

            assertEquals(SnowStormMatch.MAX_SNOWBALLS - 1, match.snowballsOf(THROWER));
            assertEquals(1, match.hitsOn(TARGET));
            assertEquals(SnowStormMatch.POINTS_PER_HIT, match.personalScore(THROWER));
            assertTrue(observer.sawEvent("snowball_hit"));
        }

        @Test
        @DisplayName("five hits put a player down and pay the knockdown bonus")
        void fiveHitsDown() {
            for (int i = 0; i < SnowStormMatch.HITS_TO_DOWN; i++) {
                throwAt(THROWER, TARGET);
            }

            assertTrue(match.isDown(TARGET));
            assertTrue(observer.sawEvent("player_down"));

            // Five hits at a point each, plus the knockdown bonus.
            int expected = SnowStormMatch.HITS_TO_DOWN * SnowStormMatch.POINTS_PER_HIT
                + SnowStormMatch.POINTS_PER_KNOCKDOWN;
            assertEquals(expected, match.personalScore(THROWER));
        }

        @Test
        @DisplayName("a downed player cannot be hit again")
        void downedPlayerIsSafe() {
            for (int i = 0; i < SnowStormMatch.HITS_TO_DOWN; i++) throwAt(THROWER, TARGET);
            int scoreWhenDowned = match.personalScore(THROWER);

            throwAt(THROWER, TARGET);

            assertEquals(scoreWhenDowned, match.personalScore(THROWER),
                "hitting someone already on the ground must not score");
        }

        @Test
        @DisplayName("a downed player cannot throw")
        void downedPlayerCannotThrow() {
            for (int i = 0; i < SnowStormMatch.HITS_TO_DOWN; i++) throwAt(THROWER, TARGET);

            match.onPlayerInput(TARGET, "throw", Map.of("targetUserId", THROWER));

            assertEquals(0, match.hitsOn(THROWER));
        }

        @Test
        @DisplayName("a downed player gets back up with a full handful")
        void recoversAfterTheTimer() {
            for (int i = 0; i < SnowStormMatch.HITS_TO_DOWN; i++) throwAt(THROWER, TARGET);
            assertTrue(match.isDown(TARGET));

            for (int i = 0; i < SnowStormMatch.DOWN_TICKS; i++) match.onTick();

            assertFalse(match.isDown(TARGET));
            assertEquals(0, match.hitsOn(TARGET), "hits reset on recovery");
            assertEquals(SnowStormMatch.MAX_SNOWBALLS, match.snowballsOf(TARGET));
            assertTrue(observer.sawEvent("player_recovered"));
        }

        @Test
        @DisplayName("you cannot hit your own team")
        void noFriendlyFire() {
            long ally = 3L;
            match.addPlayer(ally, "red");
            match.updatePosition(ally, 1, 1);

            match.onPlayerInput(THROWER, "throw", Map.of("targetUserId", ally));

            assertEquals(0, match.hitsOn(ally));
            assertEquals(SnowStormMatch.MAX_SNOWBALLS, match.snowballsOf(THROWER),
                "a refused throw must not spend a snowball");
        }

        @Test
        @DisplayName("you cannot hit yourself")
        void noSelfHits() {
            match.onPlayerInput(THROWER, "throw", Map.of("targetUserId", THROWER));

            assertEquals(0, match.hitsOn(THROWER));
        }

        @Test
        @DisplayName("a target out of range is not hit")
        void rangeIsEnforced() {
            match.updatePosition(TARGET, SnowStormMatch.THROW_RANGE + 5, 0);

            match.onPlayerInput(THROWER, "throw", Map.of("targetUserId", TARGET));

            assertEquals(0, match.hitsOn(TARGET));
            assertEquals(SnowStormMatch.MAX_SNOWBALLS, match.snowballsOf(THROWER));
        }

        @Test
        @DisplayName("throwing is rate limited")
        void cooldownBlocksSpam() {
            match.onPlayerInput(THROWER, "throw", Map.of("targetUserId", TARGET));
            // A second throw on the same tick is refused.
            match.onPlayerInput(THROWER, "throw", Map.of("targetUserId", TARGET));

            assertEquals(1, match.hitsOn(TARGET),
                "holding the button down must not throw twice at once");
        }

        @Test
        @DisplayName("an empty player cannot throw")
        void runsOutOfSnowballs() {
            for (int i = 0; i < SnowStormMatch.MAX_SNOWBALLS; i++) throwAt(THROWER, TARGET);
            assertEquals(0, match.snowballsOf(THROWER));

            int hitsBefore = match.hitsOn(TARGET);
            throwAt(THROWER, TARGET);

            assertEquals(hitsBefore, match.hitsOn(TARGET));
        }

        @Test
        @DisplayName("restocking refills one snowball at a time, up to the limit")
        void restockRefills() {
            for (int i = 0; i < SnowStormMatch.MAX_SNOWBALLS; i++) throwAt(THROWER, TARGET);
            assertEquals(0, match.snowballsOf(THROWER));

            match.onPlayerInput(THROWER, "restock", Map.of());
            for (int i = 0; i < SnowStormMatch.RESTOCK_TICKS; i++) match.onTick();

            assertEquals(1, match.snowballsOf(THROWER));

            for (int i = 0; i < SnowStormMatch.RESTOCK_TICKS * SnowStormMatch.MAX_SNOWBALLS; i++) {
                match.onTick();
            }

            assertEquals(SnowStormMatch.MAX_SNOWBALLS, match.snowballsOf(THROWER),
                "restocking must stop at the carry limit");
        }

        @Test
        @DisplayName("throwing interrupts a restock")
        void throwingStopsRestocking() {
            throwAt(THROWER, TARGET);
            match.onPlayerInput(THROWER, "restock", Map.of());
            throwAt(THROWER, TARGET);

            int afterThrow = match.snowballsOf(THROWER);
            for (int i = 0; i < SnowStormMatch.RESTOCK_TICKS * 2; i++) match.onTick();

            assertEquals(afterThrow, match.snowballsOf(THROWER),
                "a player who threw is no longer scooping");
        }

        @Test
        @DisplayName("an unnamed team is filled to the smallest side")
        void balancesTeams() {
            SnowStormMatch fresh = new SnowStormMatch(1L);
            fresh.addPlayer(10L, null);
            fresh.addPlayer(11L, null);

            assertNotEquals(fresh.teamOf(10L), fresh.teamOf(11L),
                "two unassigned players should not both land on one team");
        }

        @Test
        @DisplayName("input is ignored outside a running match")
        void ignoresInputWhenNotRunning() {
            SnowStormMatch waiting = new SnowStormMatch(1L);
            waiting.addPlayer(THROWER, "red");
            waiting.addPlayer(TARGET, "blue");

            waiting.onPlayerInput(THROWER, "throw", Map.of("targetUserId", TARGET));

            assertEquals(0, waiting.hitsOn(TARGET));
        }

        @Test
        @DisplayName("results name the winning team")
        void resultsReportAWinner() {
            throwAt(THROWER, TARGET);
            match.end();

            assertNotNull(observer.results);
            assertEquals("red", observer.results.get("winningTeam"));
            assertEquals("snowstorm", observer.results.get("gameType"));
        }
    }

    // ─── Battle Ball ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Battle Ball")
    class BattleBall {

        private BattleBallMatch match;
        private RecordingObserver observer;

        private static final long RED = 1L;
        private static final long BLUE = 2L;

        @BeforeEach
        void setUp() {
            match = new BattleBallMatch(200L);
            observer = new RecordingObserver();
            match.setObserver(observer);

            match.addPlayer(RED, "red");
            match.addPlayer(BLUE, "blue");
            match.beginPlay();
        }

        private void bounce(long user, int x, int y) {
            match.onPlayerInput(user, "bounce", Map.of("x", x, "y", y));
        }

        @Test
        @DisplayName("a bounce claims an empty tile for the team")
        void claimsTile() {
            bounce(RED, 3, 3);

            assertEquals("red", match.tileOwner(3, 3));
            assertEquals(1, match.tileBounces(3, 3));
            assertFalse(match.tileSealed(3, 3));
            assertTrue(observer.sawEvent("tile_claimed"));
        }

        @Test
        @DisplayName("three bounces seal a tile")
        void threeBouncesSeal() {
            for (int i = 0; i < BattleBallMatch.BOUNCES_TO_SEAL; i++) bounce(RED, 3, 3);

            assertTrue(match.tileSealed(3, 3));
            assertTrue(observer.sawEvent("tile_sealed"));
        }

        @Test
        @DisplayName("a sealed tile cannot be taken")
        void sealedTileIsSafe() {
            for (int i = 0; i < BattleBallMatch.BOUNCES_TO_SEAL; i++) bounce(RED, 3, 3);

            bounce(BLUE, 3, 3);

            assertEquals("red", match.tileOwner(3, 3),
                "sealing is the whole point; an opponent must not undo it");
        }

        @Test
        @DisplayName("taking an unsealed tile resets its progress")
        void stealingResetsProgress() {
            bounce(RED, 4, 4);
            bounce(RED, 4, 4);
            assertEquals(2, match.tileBounces(4, 4));

            bounce(BLUE, 4, 4);

            assertEquals("blue", match.tileOwner(4, 4));
            assertEquals(1, match.tileBounces(4, 4),
                "a stolen tile starts again for the new team");
        }

        @Test
        @DisplayName("sealing is worth more than claiming")
        void sealingScoresMore() {
            bounce(RED, 1, 1);
            int afterClaim = match.getScores().get("red");

            bounce(RED, 1, 1);
            bounce(RED, 1, 1);
            int afterSeal = match.getScores().get("red");

            assertEquals(BattleBallMatch.POINTS_CLAIM, afterClaim);
            assertEquals(
                BattleBallMatch.POINTS_CLAIM + BattleBallMatch.POINTS_HARDEN + BattleBallMatch.POINTS_SEAL,
                afterSeal);
        }

        @Test
        @DisplayName("a bounce off the floor is ignored")
        void ignoresOutOfBounds() {
            bounce(RED, -1, 0);
            bounce(RED, BattleBallMatch.FIELD_W, 0);

            assertEquals(0, match.getScores().get("red"));
        }

        @Test
        @DisplayName("supports four teams")
        void fourTeams() {
            match.addPlayer(3L, "green");
            match.addPlayer(4L, "yellow");

            bounce(3L, 5, 5);
            bounce(4L, 6, 6);

            assertEquals("green", match.tileOwner(5, 5));
            assertEquals("yellow", match.tileOwner(6, 6));
        }

        @Test
        @DisplayName("a drill seals a tile in one bounce, even an enemy's")
        void drillSealsOutright() {
            bounce(BLUE, 7, 7);

            var red = match.playerOf(RED);
            match.applyPowerup(red, BattleBallMatch.Powerup.POWER_DRILL, 7, 7);
            bounce(RED, 7, 7);

            assertTrue(match.tileSealed(7, 7));
            assertEquals("red", match.tileOwner(7, 7));
        }

        @Test
        @DisplayName("a lightbulb doubles the next few tiles")
        void lightbulbDoublesScore() {
            var red = match.playerOf(RED);
            match.applyPowerup(red, BattleBallMatch.Powerup.LIGHTBULB, 0, 0);

            bounce(RED, 2, 2);

            assertEquals(BattleBallMatch.POINTS_CLAIM * 2, match.getScores().get("red"));
        }

        @Test
        @DisplayName("pins strip surrounding enemy tiles")
        void pinsClearEnemyTiles() {
            bounce(BLUE, 5, 5);
            bounce(BLUE, 5, 6);

            var red = match.playerOf(RED);
            match.applyPowerup(red, BattleBallMatch.Powerup.BOX_OF_PINS, 5, 5);

            assertNull(match.tileOwner(5, 6), "an adjacent enemy tile should be cleared");
        }

        @Test
        @DisplayName("a bomb seals the team's own surrounding tiles")
        void bombSealsOwnTiles() {
            bounce(RED, 5, 5);
            bounce(RED, 5, 6);
            assertFalse(match.tileSealed(5, 6));

            var red = match.playerOf(RED);
            match.applyPowerup(red, BattleBallMatch.Powerup.BATTLE_BOMB, 5, 5);

            assertTrue(match.tileSealed(5, 6));
        }

        @Test
        @DisplayName("results count the tiles each team holds")
        void resultsCountTiles() {
            bounce(RED, 1, 1);
            bounce(BLUE, 2, 2);
            match.end();

            @SuppressWarnings("unchecked")
            Map<String, Integer> held = (Map<String, Integer>) observer.results.get("tilesHeld");

            assertEquals(1, held.get("red"));
            assertEquals(1, held.get("blue"));
        }
    }

    // ─── Wobble Squabble ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Wobble Squabble")
    class WobbleSquabble {

        private WobbleSquabbleMatch match;
        private RecordingObserver observer;

        private static final long ONE = 1L;
        private static final long TWO = 2L;

        @BeforeEach
        void setUp() {
            match = new WobbleSquabbleMatch(300L);
            observer = new RecordingObserver();
            match.setObserver(observer);

            match.addPlayer(ONE, null);
            match.addPlayer(TWO, null);
            match.beginPlay();
        }

        @Test
        @DisplayName("only two players can take part")
        void isADuel() {
            match.addPlayer(3L, null);

            assertEquals(2, match.getPlayers().size());
        }

        @Test
        @DisplayName("a shove pushes the opponent further off balance")
        void shovePushesOpponent() {
            match.setBalance(TWO, 10);

            match.onPlayerInput(ONE, "shove", Map.of());

            assertEquals(10 + WobbleSquabbleMatch.SHOVE_FORCE, match.balanceOf(TWO));
            assertTrue(observer.sawEvent("shoved"));
        }

        @Test
        @DisplayName("a shove pushes in whichever way the opponent is already tipping")
        void shoveCompoundsTheLean() {
            match.setBalance(TWO, -10);

            match.onPlayerInput(ONE, "shove", Map.of());

            assertEquals(-10 - WobbleSquabbleMatch.SHOVE_FORCE, match.balanceOf(TWO),
                "a shove should worsen the lean, not cancel it");
        }

        @Test
        @DisplayName("leaning in hits harder but costs your own footing")
        void leaningCostsBalance() {
            match.setBalance(ONE, 10);
            match.setBalance(TWO, 10);

            match.onPlayerInput(ONE, "lean", Map.of());

            assertEquals(10 + WobbleSquabbleMatch.LEAN_SHOVE_FORCE, match.balanceOf(TWO));
            assertEquals(10 + WobbleSquabbleMatch.LEAN_SELF_COST, match.balanceOf(ONE));
        }

        @Test
        @DisplayName("steadying pulls back toward centre without overshooting")
        void steadyRecovers() {
            match.setBalance(ONE, 10);

            match.onPlayerInput(ONE, "steady", Map.of());

            assertEquals(0, match.balanceOf(ONE),
                "steadying from inside the recovery range should land on centre, not past it");
        }

        @Test
        @DisplayName("actions are rate limited")
        void cooldownBlocksSpam() {
            match.setBalance(TWO, 0);

            match.onPlayerInput(ONE, "shove", Map.of());
            match.onPlayerInput(ONE, "shove", Map.of());

            assertEquals(WobbleSquabbleMatch.SHOVE_FORCE, match.balanceOf(TWO),
                "only the first shove of a cooldown should land");
        }

        @Test
        @DisplayName("balance never passes the limit")
        void balanceIsClamped() {
            match.setBalance(TWO, WobbleSquabbleMatch.BALANCE_LIMIT - 1);

            match.onPlayerInput(ONE, "lean", Map.of());

            assertEquals(WobbleSquabbleMatch.BALANCE_LIMIT, match.balanceOf(TWO));
        }

        @Test
        @DisplayName("tipping past the limit loses the round")
        void fallingLosesTheRound() {
            match.setBalance(TWO, WobbleSquabbleMatch.BALANCE_LIMIT);

            match.onTick();

            assertEquals(1, match.roundsWonBy(ONE));
            assertTrue(observer.sawEvent("player_fell"));
            assertEquals(2, match.currentRound(), "a new round should begin");
        }

        @Test
        @DisplayName("a new round resets both players to centre")
        void roundResetsBalance() {
            match.setBalance(TWO, WobbleSquabbleMatch.BALANCE_LIMIT);
            match.onTick();

            assertEquals(0, match.balanceOf(TWO));
            assertEquals(0, match.balanceOf(ONE));
        }

        @Test
        @DisplayName("winning enough rounds ends the match")
        void matchEndsAfterEnoughRounds() {
            for (int i = 0; i < WobbleSquabbleMatch.ROUNDS_TO_WIN; i++) {
                match.setBalance(TWO, WobbleSquabbleMatch.BALANCE_LIMIT);
                match.onTick();
            }

            assertEquals(GameState.ENDED, match.getState());
            assertEquals(ONE, observer.results.get("winnerId"));
        }

        @Test
        @DisplayName("balance drifts on its own, so standing still is not safe")
        void balanceDrifts() {
            match.setBalance(ONE, 0);
            int before = match.balanceOf(ONE);

            match.onTick();

            assertNotEquals(before, match.balanceOf(ONE));
        }

        @Test
        @DisplayName("a player leaving ends the duel")
        void leavingEndsTheDuel() {
            match.removePlayer(TWO);

            assertEquals(GameState.ENDED, match.getState());
        }
    }

    // ─── Lido Diving ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Lido Diving")
    class LidoDiving {

        private LidoDivingMatch match;
        private RecordingObserver observer;

        private static final long DIVER = 1L;

        @BeforeEach
        void setUp() {
            match = new LidoDivingMatch(400L);
            observer = new RecordingObserver();
            match.setObserver(observer);

            match.addPlayer(DIVER, null);
            match.beginPlay();
        }

        /** Starts a dive and releases it exactly n ticks in. */
        private void dive(LidoDivingMatch.Dive type, int releaseAt) {
            match.onPlayerInput(DIVER, "dive", Map.of("dive", type.name()));
            match.advanceFlight(DIVER, releaseAt);
            match.onPlayerInput(DIVER, "release", Map.of());
        }

        @Test
        @DisplayName("a dive starts and reports its window")
        void diveStarts() {
            match.onPlayerInput(DIVER, "dive", Map.of("dive", "PIKE"));

            assertTrue(match.isDiving(DIVER));
            assertTrue(observer.sawEvent("dive_started"));
        }

        @Test
        @DisplayName("a perfectly timed dive scores its full difficulty")
        void perfectTimingScoresFully() {
            dive(LidoDivingMatch.Dive.SWAN, LidoDivingMatch.PERFECT_TICK);

            int expected = (int) Math.round(
                LidoDivingMatch.BASE_POINTS * LidoDivingMatch.Dive.SWAN.difficulty);
            assertEquals(expected, match.totalOf(DIVER));
        }

        @Test
        @DisplayName("a harder dive taken perfectly beats an easier one")
        void difficultyMatters() {
            dive(LidoDivingMatch.Dive.PENCIL, LidoDivingMatch.PERFECT_TICK);
            int easy = match.totalOf(DIVER);

            LidoDivingMatch other = new LidoDivingMatch(1L);
            other.addPlayer(DIVER, null);
            other.beginPlay();
            other.onPlayerInput(DIVER, "dive", Map.of("dive", "SWAN"));
            other.advanceFlight(DIVER, LidoDivingMatch.PERFECT_TICK);
            other.onPlayerInput(DIVER, "release", Map.of());

            assertTrue(other.totalOf(DIVER) > easy);
        }

        @Test
        @DisplayName("mistiming costs points either side of perfect")
        void mistimingIsPenalisedSymmetrically() {
            LidoDivingMatch early = new LidoDivingMatch(1L);
            early.addPlayer(DIVER, null);
            early.beginPlay();
            early.onPlayerInput(DIVER, "dive", Map.of("dive", "PIKE"));
            early.advanceFlight(DIVER, LidoDivingMatch.PERFECT_TICK - 4);
            early.onPlayerInput(DIVER, "release", Map.of());

            LidoDivingMatch late = new LidoDivingMatch(2L);
            late.addPlayer(DIVER, null);
            late.beginPlay();
            late.onPlayerInput(DIVER, "dive", Map.of("dive", "PIKE"));
            late.advanceFlight(DIVER, LidoDivingMatch.PERFECT_TICK + 4);
            late.onPlayerInput(DIVER, "release", Map.of());

            assertEquals(early.totalOf(DIVER), late.totalOf(DIVER),
                "an early release should cost exactly what an equally late one does");
        }

        @Test
        @DisplayName("a dive counts against the diver's attempts")
        void divesAreCounted() {
            dive(LidoDivingMatch.Dive.TUCK, LidoDivingMatch.PERFECT_TICK);

            assertEquals(1, match.divesTakenBy(DIVER));
            assertFalse(match.isDiving(DIVER));
        }

        @Test
        @DisplayName("a diver cannot start a second dive mid-air")
        void oneDiveAtATime() {
            match.onPlayerInput(DIVER, "dive", Map.of("dive", "PIKE"));
            match.onPlayerInput(DIVER, "dive", Map.of("dive", "SWAN"));

            match.advanceFlight(DIVER, LidoDivingMatch.PERFECT_TICK);
            match.onPlayerInput(DIVER, "release", Map.of());

            assertEquals(1, match.divesTakenBy(DIVER));
        }

        @Test
        @DisplayName("a diver who never releases still uses the attempt")
        void neverReleasingStillCounts() {
            match.onPlayerInput(DIVER, "dive", Map.of("dive", "PIKE"));

            for (int i = 0; i <= LidoDivingMatch.DIVE_WINDOW_TICKS + 1; i++) match.onTick();

            assertEquals(1, match.divesTakenBy(DIVER),
                "waiting out the window must not be a way to avoid a bad attempt");
            assertFalse(match.isDiving(DIVER));
        }

        @Test
        @DisplayName("the match ends once everyone has used their dives")
        void endsWhenAllDivesAreTaken() {
            for (int i = 0; i < LidoDivingMatch.DIVES_PER_PLAYER; i++) {
                dive(LidoDivingMatch.Dive.TUCK, LidoDivingMatch.PERFECT_TICK);
            }
            match.onTick();

            assertEquals(GameState.ENDED, match.getState());
            assertEquals(DIVER, observer.results.get("winnerId"));
        }

        @Test
        @DisplayName("a diver with no attempts left cannot dive again")
        void attemptsAreLimited() {
            for (int i = 0; i < LidoDivingMatch.DIVES_PER_PLAYER; i++) {
                dive(LidoDivingMatch.Dive.TUCK, LidoDivingMatch.PERFECT_TICK);
            }

            match.onPlayerInput(DIVER, "dive", Map.of("dive", "SWAN"));

            assertFalse(match.isDiving(DIVER));
            assertEquals(LidoDivingMatch.DIVES_PER_PLAYER, match.divesTakenBy(DIVER));
        }

        @Test
        @DisplayName("an unknown dive is refused")
        void unknownDiveIsRefused() {
            match.onPlayerInput(DIVER, "dive", Map.of("dive", "BACKFLIP_OF_DOOM"));

            assertFalse(match.isDiving(DIVER));
        }

        @Test
        @DisplayName("releasing without diving does nothing")
        void releaseWithoutDiveIsHarmless() {
            assertDoesNotThrow(() -> match.onPlayerInput(DIVER, "release", Map.of()));
            assertEquals(0, match.divesTakenBy(DIVER));
        }
    }

    // ─── engine wiring ──────────────────────────────────────────────────────

    @Test
    @DisplayName("every classic game is reachable by name")
    void engineKnowsEveryClassicGame() {
        // A game the factory cannot build is a game nobody can start.
        for (String type : List.of("snowstorm", "battleball", "wobblesquabble", "lidodiving")) {
            assertDoesNotThrow(() -> {
                GameMatch match = switch (type) {
                    case "snowstorm"      -> new SnowStormMatch(1L);
                    case "battleball"     -> new BattleBallMatch(1L);
                    case "wobblesquabble" -> new WobbleSquabbleMatch(1L);
                    case "lidodiving"     -> new LidoDivingMatch(1L);
                    default -> throw new IllegalStateException(type);
                };
                assertEquals(type, match.getGameType());
            }, type + " could not be created");
        }
    }
}
