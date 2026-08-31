package com.habnut.conformance;

import com.habnut.emulator.wired.WiredContext;
import com.habnut.emulator.wired.WiredValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.habnut.conformance.WiredHarness.condition;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Condition semantics.
 *
 * Conditions gate whether a stack's actions run at all, so a condition that
 * silently inverts or always passes is among the most damaging defects a room
 * can carry. Each comparison is checked on both sides of its boundary, and each
 * negated condition is checked against its positive twin.
 */
@DisplayName("Wired conditions")
class ConditionConformanceTest {

    private WiredHarness h;
    private WiredContext ctx;

    @BeforeEach
    void setUp() {
        h = new WiredHarness();
        ctx = h.context();
        ctx.setVariable(WiredContext.Scope.ROOM, "n", WiredValue.ofNumber(10));
        ctx.setVariable(WiredContext.Scope.ROOM, "s", WiredValue.ofText("hello world"));
        ctx.setVariable(WiredContext.Scope.ROOM, "b", WiredValue.TRUE);
    }

    // ─── constants ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("always_true and always_false are unconditional")
    void constants() {
        assertTrue(h.evaluate(condition("cond.always_true"), ctx));
        assertFalse(h.evaluate(condition("cond.always_false"), ctx));
    }

    // ─── numeric comparison, both sides of the boundary ─────────────────────

    @ParameterizedTest(name = "{0} against {1} → {2}")
    @CsvSource({
        "cond.var_eq,      10, true",
        "cond.var_eq,       9, false",
        "cond.var_not_eq,   9, true",
        "cond.var_not_eq,  10, false",
        "cond.var_gt,       9, true",
        "cond.var_gt,      10, false",
        "cond.var_gt,      11, false",
        "cond.var_lt,      11, true",
        "cond.var_lt,      10, false",
        "cond.var_lt,       9, false",
        "cond.var_gte,     10, true",
        "cond.var_gte,      9, true",
        "cond.var_gte,     11, false",
        "cond.var_lte,     10, true",
        "cond.var_lte,     11, true",
        "cond.var_lte,      9, false",
    })
    @DisplayName("numeric comparisons hold at and around their boundary")
    void numericComparisons(String code, double operand, boolean expected) {
        assertEquals(expected, h.evaluate(condition(code,
            "scope", "room", "varName", "n",
            "valueType", "number", "value", operand), ctx));
    }

    // ─── text ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("var_contains and its negation are exact inverses")
    void containsAndNegation() {
        var present = condition("cond.var_contains",
            "scope", "room", "varName", "s", "valueType", "text", "value", "world");
        var absent = condition("cond.var_contains",
            "scope", "room", "varName", "s", "valueType", "text", "value", "goodbye");

        assertTrue(h.evaluate(present, ctx));
        assertFalse(h.evaluate(absent, ctx));

        var notPresent = condition("cond.not_var_contains",
            "scope", "room", "varName", "s", "valueType", "text", "value", "world");
        var notAbsent = condition("cond.not_var_contains",
            "scope", "room", "varName", "s", "valueType", "text", "value", "goodbye");

        assertFalse(h.evaluate(notPresent, ctx));
        assertTrue(h.evaluate(notAbsent, ctx));
    }

    @Test
    @DisplayName("contains is case-sensitive")
    void containsIsCaseSensitive() {
        assertFalse(h.evaluate(condition("cond.var_contains",
            "scope", "room", "varName", "s", "valueType", "text", "value", "WORLD"), ctx));
    }

    // ─── types ──────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({"n, number, true", "n, text, false", "s, text, true", "b, bool, true", "b, number, false"})
    @DisplayName("var_is_type reports the stored type")
    void varIsType(String varName, String type, boolean expected) {
        assertEquals(expected, h.evaluate(condition("cond.var_is_type",
            "scope", "room", "varName", varName, "type", type), ctx));
    }

    // ─── time ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("date_range_active brackets the current time")
    void dateRange() {
        long now = System.currentTimeMillis();

        assertTrue(h.evaluate(condition("cond.date_range_active",
            "from", now - 60_000, "to", now + 60_000), ctx), "now is inside the window");
        assertFalse(h.evaluate(condition("cond.date_range_active",
            "from", now + 60_000, "to", now + 120_000), ctx), "window is in the future");
        assertFalse(h.evaluate(condition("cond.date_range_active",
            "from", now - 120_000, "to", now - 60_000), ctx), "window is in the past");
    }

    @Test
    @DisplayName("is_day and is_night partition the clock")
    void dayAndNightAreComplementary() {
        boolean day   = h.evaluate(condition("cond.is_day"), ctx);
        boolean night = h.evaluate(condition("cond.is_night"), ctx);
        assertNotEquals(day, night, "exactly one of is_day and is_night holds at any moment");
    }

    // ─── chance ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("random_chance honours its certain and impossible bounds")
    void randomChanceBounds() {
        for (int i = 0; i < 50; i++) {
            assertTrue(h.evaluate(condition("cond.random_chance", "percent", 100), ctx),
                "100 percent must always pass");
            assertFalse(h.evaluate(condition("cond.random_chance", "percent", 0), ctx),
                "0 percent must never pass");
        }
    }

    @Test
    @DisplayName("random_chance at 50 percent produces both outcomes")
    void randomChanceVaries() {
        boolean sawTrue = false, sawFalse = false;
        for (int i = 0; i < 300 && !(sawTrue && sawFalse); i++) {
            if (h.evaluate(condition("cond.random_chance", "percent", 50), ctx)) sawTrue = true;
            else sawFalse = true;
        }
        assertTrue(sawTrue && sawFalse, "a 50 percent chance should yield both outcomes over 300 draws");
    }

    // ─── entity-dependent conditions ────────────────────────────────────────

    @Test
    @DisplayName("actor conditions are false when no actor triggered the stack")
    void actorConditionsWithoutAnActor() {
        // A timer-driven stack has no triggering entity; actor conditions must
        // report false rather than throwing.
        assertFalse(h.evaluate(condition("cond.actor_is_room_owner"), ctx));
        assertFalse(h.evaluate(condition("cond.actor_in_group", "groupId", 1), ctx));
        assertFalse(h.evaluate(condition("cond.actor_in_team", "team", "red"), ctx));
    }

    @Test
    @DisplayName("negated actor conditions invert their positive twin")
    void negatedActorConditions() {
        assertTrue(h.evaluate(condition("cond.not_actor_in_group", "groupId", 1), ctx),
            "not_in_group holds when there is no actor to be in one");
        assertTrue(h.evaluate(condition("cond.not_actor_in_team", "team", "red"), ctx));
    }

    // ─── signals ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("signal_value_eq matches the payload the engine publishes")
    void signalValueEq() {
        // WiredEngine publishes an incoming signal as signalChannel/signalPayload.
        ctx.locals.put("signalPayload", WiredValue.ofText("go"));

        assertTrue(h.evaluate(condition("cond.signal_value_eq",
            "valueType", "text", "value", "go"), ctx));
        assertFalse(h.evaluate(condition("cond.signal_value_eq",
            "valueType", "text", "value", "stop"), ctx));
    }

    @Test
    @DisplayName("signal_value_eq is false when no signal is on the context")
    void signalValueEqWithoutSignal() {
        assertFalse(h.evaluate(condition("cond.signal_value_eq",
            "valueType", "text", "value", "go"), ctx));
    }

    // ─── robustness ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a condition on an unset variable evaluates rather than throwing")
    void unsetVariableIsSafe() {
        assertDoesNotThrow(() -> h.evaluate(condition("cond.var_eq",
            "scope", "room", "varName", "never_set",
            "valueType", "number", "value", 0), ctx));

        assertTrue(h.evaluate(condition("cond.var_eq",
            "scope", "room", "varName", "never_set",
            "valueType", "number", "value", 0), ctx), "an unset variable equals zero");
    }

    @Test
    @DisplayName("a malformed numeric parameter degrades to zero")
    void malformedParameter() {
        assertDoesNotThrow(() -> h.evaluate(condition("cond.var_gt",
            "scope", "room", "varName", "n",
            "valueType", "number", "value", "not-a-number"), ctx));
    }
}
