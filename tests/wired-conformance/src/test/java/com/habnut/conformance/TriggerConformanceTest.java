package com.habnut.conformance;

import com.habnut.emulator.wired.WiredContext;
import com.habnut.emulator.wired.WiredStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.habnut.conformance.WiredHarness.trigger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Trigger semantics.
 *
 * A trigger must fire on its own event and stay silent on every other one. The
 * cross-talk checks matter most: a trigger that fires on a neighbouring event
 * turns one builder's room into a stack that runs constantly.
 */
@DisplayName("Wired triggers")
class TriggerConformanceTest {

    private WiredHarness h;
    private WiredContext ctx;

    @BeforeEach
    void setUp() {
        h = new WiredHarness();
        ctx = h.context();
    }

    private boolean fires(WiredStack.WiredComponent trig) {
        return h.fires(WiredHarness.stackWith(trig), ctx);
    }

    // ─── event-keyed triggers ───────────────────────────────────────────────

    @ParameterizedTest(name = "{1} fires on triggerEvent={0}")
    @CsvSource({
        "enter_room,     trigger.enter_room",
        "leave_room,     trigger.leave_room",
        "timer,          trigger.timer",
        "game_start,     trigger.game_starts",
        "game_end,       trigger.game_ends",
        "periodic_long,  trigger.periodic_long",
        "periodic_short, trigger.periodic_short",
    })
    @DisplayName("each event trigger fires on its own event")
    void eventTriggerFires(String event, String code) {
        ctx.locals.put("triggerEvent", event);
        assertTrue(fires(trigger(code)));
    }

    @ParameterizedTest(name = "{1} stays silent on triggerEvent={0}")
    @CsvSource({
        "leave_room,     trigger.enter_room",
        "enter_room,     trigger.leave_room",
        "game_end,       trigger.game_starts",
        "game_start,     trigger.game_ends",
        "periodic_short, trigger.periodic_long",
        "periodic_long,  trigger.periodic_short",
    })
    @DisplayName("event triggers do not fire on a neighbouring event")
    void eventTriggerDoesNotCrossFire(String event, String code) {
        ctx.locals.put("triggerEvent", event);
        assertFalse(fires(trigger(code)));
    }

    @Test
    @DisplayName("no trigger fires on an empty context")
    void nothingFiresOnAnEmptyContext() {
        for (String code : RegistryConformanceTest.TRIGGERS) {
            assertFalse(fires(trigger(code)),
                code + " fired with no event on the context");
        }
    }

    // ─── chat ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("say matches the whole message, case-insensitively")
    void sayMatchesWholeMessage() {
        ctx.locals.put("chatMessage", "hello");

        assertTrue(fires(trigger("trigger.say", "message", "hello")));
        assertTrue(fires(trigger("trigger.say", "message", "HELLO")), "case-insensitive");
        assertFalse(fires(trigger("trigger.say", "message", "hell")), "partial must not match");
        assertFalse(fires(trigger("trigger.say", "message", "hello there")));
    }

    @Test
    @DisplayName("say with a blank parameter matches any message")
    void sayWithBlankMatchesAnything() {
        ctx.locals.put("chatMessage", "anything at all");
        assertTrue(fires(trigger("trigger.say", "message", "")));
    }

    @Test
    @DisplayName("say_contains matches a substring, case-insensitively")
    void sayContains() {
        ctx.locals.put("chatMessage", "Hello World");

        assertTrue(fires(trigger("trigger.say_contains", "message", "world")));
        assertTrue(fires(trigger("trigger.say_contains", "message", "WORLD")));
        assertFalse(fires(trigger("trigger.say_contains", "message", "goodbye")));
    }

    @Test
    @DisplayName("say_contains with a blank parameter matches nothing")
    void sayContainsWithBlankMatchesNothing() {
        ctx.locals.put("chatMessage", "anything");
        assertFalse(fires(trigger("trigger.say_contains", "message", "")),
            "a blank substring would otherwise fire on every message spoken");
    }

    // ─── score ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("score_achieved fires at and above its threshold")
    void scoreAchieved() {
        ctx.locals.put("gameScore", 10);

        assertTrue(fires(trigger("trigger.score_achieved", "score", 10)), "at the threshold");
        assertTrue(fires(trigger("trigger.score_achieved", "score", 5)), "above the threshold");
        assertFalse(fires(trigger("trigger.score_achieved", "score", 11)), "below the threshold");
    }

    @Test
    @DisplayName("score_achieved stays silent when no score is present")
    void scoreAchievedWithoutScore() {
        assertFalse(fires(trigger("trigger.score_achieved", "score", 1)));
    }

    // ─── signals and variables ──────────────────────────────────────────────

    @Test
    @DisplayName("signal_received fires only on its own channel")
    void signalReceived() {
        ctx.locals.put("signalChannel", "round_start");

        assertTrue(fires(trigger("trigger.signal_received", "channel", "round_start")));
        assertFalse(fires(trigger("trigger.signal_received", "channel", "round_end")));
    }

    @Test
    @DisplayName("variable_changed fires only for the named variable")
    void variableChanged() {
        ctx.locals.put("changedVarName", "score");

        assertTrue(fires(trigger("trigger.variable_changed", "varName", "score")));
        assertFalse(fires(trigger("trigger.variable_changed", "varName", "lives")));
    }

    // ─── presence-keyed triggers ────────────────────────────────────────────

    @Test
    @DisplayName("collision and bot triggers fire on the presence of their key")
    void presenceTriggers() {
        ctx.locals.put("collision", true);
        assertTrue(fires(trigger("trigger.collision")));

        WiredContext other = h.context();
        other.locals.put("botReachedFurni", 1L);
        assertTrue(h.fires(WiredHarness.stackWith(trigger("trigger.bot_reaches_furni")), other));
        assertFalse(h.fires(WiredHarness.stackWith(trigger("trigger.bot_reached_avat")), other),
            "the avatar variant must not fire on the furni key");
    }
}
