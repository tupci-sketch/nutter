package com.habnut.conformance;

import com.habnut.emulator.wired.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.habnut.conformance.WiredHarness.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Executor semantics: how conditions, selectors and actions combine, and how
 * the engine protects itself from a stack that misbehaves.
 *
 * The safety properties matter as much as the happy path. A room is untrusted
 * input — anyone can build a stack — so an unknown code, a throwing action or
 * an unbounded loop must degrade rather than take the room down.
 */
@DisplayName("Wired execution")
class ExecutionConformanceTest {

    private WiredHarness h;

    @BeforeEach
    void setUp() {
        h = new WiredHarness();
    }

    private WiredStack stack() {
        WiredStack s = new WiredStack(1L, WiredHarness.ROOM_ID);
        s.setTrigger(trigger("trigger.timer"));
        return s;
    }

    // ─── condition combination ──────────────────────────────────────────────

    @Test
    @DisplayName("a stack with no conditions runs its actions")
    void noConditionsRuns() {
        WiredStack s = stack();
        s.addAction(action("act.set_var",
            "scope", "room", "varName", "ran", "valueType", "number", "value", 1));

        WiredContext ctx = h.context();
        assertTrue(h.executor.execute(s, ctx));
        assertEquals(1, ctx.getVariable(WiredContext.Scope.ROOM, "ran").asNumber());
    }

    @Test
    @DisplayName("conditions combine with AND — every one must pass")
    void conditionsAreAnded() {
        WiredStack s = stack();
        s.addCondition(condition("cond.always_true"));
        s.addCondition(condition("cond.always_true"));
        s.addAction(action("act.set_var",
            "scope", "room", "varName", "ran", "valueType", "number", "value", 1));

        WiredContext ctx = h.context();
        assertTrue(h.executor.execute(s, ctx));
        assertEquals(1, ctx.getVariable(WiredContext.Scope.ROOM, "ran").asNumber());
    }

    @Test
    @DisplayName("a single failing condition blocks every action")
    void oneFailingConditionBlocks() {
        WiredStack s = stack();
        s.addCondition(condition("cond.always_true"));
        s.addCondition(condition("cond.always_false"));
        s.addAction(action("act.set_var",
            "scope", "room", "varName", "ran", "valueType", "number", "value", 1));

        WiredContext ctx = h.context();
        assertFalse(h.executor.execute(s, ctx));
        assertEquals(0, ctx.getVariable(WiredContext.Scope.ROOM, "ran").asNumber(),
            "the action must not have run");
    }

    @Test
    @DisplayName("a stack with no trigger never executes")
    void noTriggerNeverExecutes() {
        WiredStack s = new WiredStack(1L, WiredHarness.ROOM_ID);
        s.addAction(action("act.set_var",
            "scope", "room", "varName", "ran", "valueType", "number", "value", 1));

        WiredContext ctx = h.context();
        assertFalse(h.executor.execute(s, ctx));
        assertEquals(0, ctx.getVariable(WiredContext.Scope.ROOM, "ran").asNumber());
    }

    // ─── ordering ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("actions run in the order they were added")
    void actionsRunInOrder() {
        WiredStack s = stack();
        s.addAction(action("act.set_var",
            "scope", "room", "varName", "n", "valueType", "number", "value", 10));
        s.addAction(action("act.mul_var",
            "scope", "room", "varName", "n", "valueType", "number", "value", 3));
        s.addAction(action("act.sub_var",
            "scope", "room", "varName", "n", "valueType", "number", "value", 5));

        WiredContext ctx = h.context();
        h.executor.execute(s, ctx);

        assertEquals(25, ctx.getVariable(WiredContext.Scope.ROOM, "n").asNumber(),
            "(10 * 3) - 5 — any other order gives a different answer");
    }

    // ─── selectors ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("a selector publishes its result onto the context")
    void selectorPublishesTargets() {
        WiredStack s = stack();
        s.addSelector(selector("sel.empty"));
        s.addAction(action("act.no_op"));

        WiredContext ctx = h.context();
        assertTrue(h.executor.execute(s, ctx));
        assertTrue(ctx.locals.containsKey("selectedFurnis"),
            "the executor should record the selection for actions to consume");
    }

    // ─── robustness ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("an unknown condition blocks the stack instead of throwing")
    void unknownConditionBlocks() {
        WiredStack s = stack();
        s.addCondition(condition("cond.invented_by_a_client"));
        s.addAction(action("act.set_var",
            "scope", "room", "varName", "ran", "valueType", "number", "value", 1));

        WiredContext ctx = h.context();
        assertFalse(assertDoesNotThrow(() -> h.executor.execute(s, ctx)));
        assertEquals(0, ctx.getVariable(WiredContext.Scope.ROOM, "ran").asNumber());
    }

    @Test
    @DisplayName("an unknown action is skipped and the rest still run")
    void unknownActionIsSkipped() {
        WiredStack s = stack();
        s.addAction(action("act.invented_by_a_client"));
        s.addAction(action("act.set_var",
            "scope", "room", "varName", "ran", "valueType", "number", "value", 1));

        WiredContext ctx = h.context();
        assertTrue(assertDoesNotThrow(() -> h.executor.execute(s, ctx)));
        assertEquals(1, ctx.getVariable(WiredContext.Scope.ROOM, "ran").asNumber(),
            "a later valid action must still run");
    }

    @Test
    @DisplayName("an action that throws does not abort the remaining actions")
    void throwingActionIsContained() {
        WiredStack s = stack();
        // Acting on room furniture with no room present throws inside the action.
        s.addAction(action("act.move_furni", "furniId", 1, "x", 1, "y", 1));
        s.addAction(action("act.set_var",
            "scope", "room", "varName", "ran", "valueType", "number", "value", 1));

        WiredContext ctx = h.context();
        assertTrue(assertDoesNotThrow(() -> h.executor.execute(s, ctx)));
        assertEquals(1, ctx.getVariable(WiredContext.Scope.ROOM, "ran").asNumber());
    }

    // ─── execution limiter ──────────────────────────────────────────────────

    @Test
    @DisplayName("the operation limiter stops a runaway stack")
    void operationLimiterFires() {
        WiredContext ctx = h.context();

        assertThrows(WiredExecutionLimitException.class, () -> {
            for (int i = 0; i < 10_000; i++) ctx.tick();
        }, "an unbounded stack must be cut off rather than spinning forever");
    }

    @Test
    @DisplayName("a normal stack stays well inside the operation limit")
    void normalStackStaysUnderTheLimit() {
        WiredStack s = stack();
        for (int i = 0; i < 20; i++) {
            s.addCondition(condition("cond.always_true"));
            s.addAction(action("act.no_op"));
        }

        WiredContext ctx = h.context();
        assertTrue(assertDoesNotThrow(() -> h.executor.execute(s, ctx)));
    }

    @Test
    @DisplayName("elapsed time is reported for the debugger")
    void elapsedTimeIsTracked() {
        WiredContext ctx = h.context();
        assertTrue(ctx.elapsedMs() >= 0);
    }

    // ─── stack composition ──────────────────────────────────────────────────

    @Test
    @DisplayName("component lists are unmodifiable to callers")
    void componentListsAreUnmodifiable() {
        WiredStack s = stack();
        s.addAction(action("act.no_op"));

        assertThrows(UnsupportedOperationException.class,
            () -> s.getActions().add(action("act.no_op")));
    }

    @Test
    @DisplayName("clearing a component list empties it")
    void clearingComponents() {
        WiredStack s = stack();
        s.addAction(action("act.no_op"));
        s.addCondition(condition("cond.always_true"));
        s.addSelector(selector("sel.empty"));

        s.clearActions();
        s.clearConditions();
        s.clearSelectors();

        assertTrue(s.getActions().isEmpty());
        assertTrue(s.getConditions().isEmpty());
        assertTrue(s.getSelectors().isEmpty());
    }
}
