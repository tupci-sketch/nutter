package com.habnut.conformance;

import com.habnut.emulator.wired.WiredContext;
import com.habnut.emulator.wired.WiredValue;
import com.habnut.emulator.wired.WiredVariableStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.habnut.conformance.WiredHarness.action;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Variable scoping and the arithmetic actions that operate on variables.
 *
 * Scope isolation is the property builders depend on most: a room variable in
 * one room must never be visible from another, and a global must be visible
 * from everywhere.
 */
@DisplayName("Wired variables")
class VariableConformanceTest {

    private WiredHarness h;

    @BeforeEach
    void setUp() {
        h = new WiredHarness();
    }

    // ─── scoping ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("an unset variable reads as zero")
    void unsetReadsZero() {
        assertEquals(0, h.context().getVariable(WiredContext.Scope.ROOM, "never_set").asNumber());
    }

    @Test
    @DisplayName("room variables do not leak between rooms")
    void roomScopeIsolatesRooms() {
        WiredContext a = h.context(WiredHarness.ROOM_ID);
        WiredContext b = h.context(WiredHarness.OTHER_ROOM_ID);

        a.setVariable(WiredContext.Scope.ROOM, "score", WiredValue.ofNumber(50));

        assertEquals(50, a.getVariable(WiredContext.Scope.ROOM, "score").asNumber());
        assertEquals(0, b.getVariable(WiredContext.Scope.ROOM, "score").asNumber(),
            "a different room must not see the first room's variable");
    }

    @Test
    @DisplayName("global variables are visible from every room")
    void globalScopeCrossesRooms() {
        WiredContext a = h.context(WiredHarness.ROOM_ID);
        WiredContext b = h.context(WiredHarness.OTHER_ROOM_ID);

        a.setVariable(WiredContext.Scope.GLOBAL, "jackpot", WiredValue.ofNumber(999));

        assertEquals(999, b.getVariable(WiredContext.Scope.GLOBAL, "jackpot").asNumber());
    }

    @Test
    @DisplayName("the same name in different scopes holds different values")
    void scopesAreIndependentNamespaces() {
        WiredContext ctx = h.context();
        ctx.setVariable(WiredContext.Scope.ROOM,   "count", WiredValue.ofNumber(1));
        ctx.setVariable(WiredContext.Scope.GLOBAL, "count", WiredValue.ofNumber(2));

        assertEquals(1, ctx.getVariable(WiredContext.Scope.ROOM,   "count").asNumber());
        assertEquals(2, ctx.getVariable(WiredContext.Scope.GLOBAL, "count").asNumber());
    }

    @Test
    @DisplayName("evicting a room clears only that room's variables")
    void evictionIsScopedToOneRoom() {
        WiredVariableStore store = h.variables;
        store.set(WiredContext.Scope.ROOM, WiredHarness.ROOM_ID, -1, "a", WiredValue.ofNumber(1));
        store.set(WiredContext.Scope.ROOM, WiredHarness.OTHER_ROOM_ID, -1, "a", WiredValue.ofNumber(2));
        store.set(WiredContext.Scope.GLOBAL, 0, -1, "g", WiredValue.ofNumber(3));

        store.evictRoom(WiredHarness.ROOM_ID);

        assertEquals(0, store.get(WiredContext.Scope.ROOM, WiredHarness.ROOM_ID, -1, "a").asNumber(),
            "evicted room variable should read as unset");
        assertEquals(2, store.get(WiredContext.Scope.ROOM, WiredHarness.OTHER_ROOM_ID, -1, "a").asNumber(),
            "another room must be untouched");
        assertEquals(3, store.get(WiredContext.Scope.GLOBAL, 0, -1, "g").asNumber(),
            "globals must survive a room eviction");
    }

    @Test
    @DisplayName("variables hold each of the three types")
    void allTypesRoundTrip() {
        WiredContext ctx = h.context();
        ctx.setVariable(WiredContext.Scope.ROOM, "n", WiredValue.ofNumber(1.5));
        ctx.setVariable(WiredContext.Scope.ROOM, "t", WiredValue.ofText("hello"));
        ctx.setVariable(WiredContext.Scope.ROOM, "b", WiredValue.TRUE);

        assertEquals(1.5, ctx.getVariable(WiredContext.Scope.ROOM, "n").asNumber());
        assertEquals("hello", ctx.getVariable(WiredContext.Scope.ROOM, "t").asText());
        assertTrue(ctx.getVariable(WiredContext.Scope.ROOM, "b").asBool());
    }

    // ─── variable actions ───────────────────────────────────────────────────

    @Test
    @DisplayName("set_var writes a literal")
    void setVar() {
        WiredContext ctx = h.context();
        h.execute(action("act.set_var",
            "scope", "room", "varName", "hp", "valueType", "number", "value", 100), ctx);

        assertEquals(100, ctx.getVariable(WiredContext.Scope.ROOM, "hp").asNumber());
    }

    @Test
    @DisplayName("the arithmetic actions accumulate onto the current value")
    void arithmeticActions() {
        WiredContext ctx = h.context();

        h.execute(action("act.set_var",
            "scope", "room", "varName", "n", "valueType", "number", "value", 10), ctx);
        h.execute(action("act.add_var",
            "scope", "room", "varName", "n", "valueType", "number", "value", 5), ctx);
        assertEquals(15, ctx.getVariable(WiredContext.Scope.ROOM, "n").asNumber(), "add");

        h.execute(action("act.sub_var",
            "scope", "room", "varName", "n", "valueType", "number", "value", 3), ctx);
        assertEquals(12, ctx.getVariable(WiredContext.Scope.ROOM, "n").asNumber(), "sub");

        h.execute(action("act.mul_var",
            "scope", "room", "varName", "n", "valueType", "number", "value", 2), ctx);
        assertEquals(24, ctx.getVariable(WiredContext.Scope.ROOM, "n").asNumber(), "mul");

        h.execute(action("act.div_var",
            "scope", "room", "varName", "n", "valueType", "number", "value", 4), ctx);
        assertEquals(6, ctx.getVariable(WiredContext.Scope.ROOM, "n").asNumber(), "div");

        h.execute(action("act.mod_var",
            "scope", "room", "varName", "n", "valueType", "number", "value", 4), ctx);
        assertEquals(2, ctx.getVariable(WiredContext.Scope.ROOM, "n").asNumber(), "mod");
    }

    @Test
    @DisplayName("dividing a variable by zero leaves it at zero rather than failing")
    void divideByZeroIsSurvivable() {
        WiredContext ctx = h.context();
        h.execute(action("act.set_var",
            "scope", "room", "varName", "n", "valueType", "number", "value", 10), ctx);

        assertDoesNotThrow(() -> h.execute(action("act.div_var",
            "scope", "room", "varName", "n", "valueType", "number", "value", 0), ctx));
        assertEquals(0, ctx.getVariable(WiredContext.Scope.ROOM, "n").asNumber());
    }

    @Test
    @DisplayName("reset_var returns a variable to zero")
    void resetVar() {
        WiredContext ctx = h.context();
        ctx.setVariable(WiredContext.Scope.ROOM, "n", WiredValue.ofNumber(42));

        h.execute(action("act.reset_var", "scope", "room", "varName", "n"), ctx);

        assertEquals(0, ctx.getVariable(WiredContext.Scope.ROOM, "n").asNumber());
    }

    @Test
    @DisplayName("concat_var appends text")
    void concatVar() {
        WiredContext ctx = h.context();
        h.execute(action("act.set_var",
            "scope", "room", "varName", "s", "valueType", "text", "value", "Hello"), ctx);
        h.execute(action("act.concat_var",
            "scope", "room", "varName", "s", "valueType", "text", "value", " World"), ctx);

        assertEquals("Hello World", ctx.getVariable(WiredContext.Scope.ROOM, "s").asText());
    }

    @Test
    @DisplayName("one variable can be added to another across scopes")
    void variableAsOperand() {
        WiredContext ctx = h.context();
        ctx.setVariable(WiredContext.Scope.GLOBAL, "bonus", WiredValue.ofNumber(25));
        ctx.setVariable(WiredContext.Scope.ROOM,   "score", WiredValue.ofNumber(100));

        h.execute(action("act.add_var",
            "scope", "room", "varName", "score",
            "valueType", "variable", "valueScope", "global", "valueVarName", "bonus"), ctx);

        assertEquals(125, ctx.getVariable(WiredContext.Scope.ROOM, "score").asNumber(),
            "the room variable should absorb the global operand");
        assertEquals(25, ctx.getVariable(WiredContext.Scope.GLOBAL, "bonus").asNumber(),
            "the operand variable must not be modified");
    }

    @Test
    @DisplayName("omitting the operand keys reads the target as its own operand")
    void operandDefaultsToTarget() {
        WiredContext ctx = h.context();
        ctx.setVariable(WiredContext.Scope.ROOM, "n", WiredValue.ofNumber(7));

        h.execute(action("act.add_var",
            "scope", "room", "varName", "n", "valueType", "variable"), ctx);

        assertEquals(14, ctx.getVariable(WiredContext.Scope.ROOM, "n").asNumber(),
            "n + n");
    }

    @Test
    @DisplayName("a condition can compare two variables")
    void conditionComparesTwoVariables() {
        WiredContext ctx = h.context();
        ctx.setVariable(WiredContext.Scope.ROOM, "a", WiredValue.ofNumber(10));
        ctx.setVariable(WiredContext.Scope.ROOM, "b", WiredValue.ofNumber(4));

        assertTrue(h.evaluate(WiredHarness.condition("cond.var_gt",
            "scope", "room", "varName", "a",
            "valueType", "variable", "valueScope", "room", "valueVarName", "b"), ctx));

        assertFalse(h.evaluate(WiredHarness.condition("cond.var_lt",
            "scope", "room", "varName", "a",
            "valueType", "variable", "valueScope", "room", "valueVarName", "b"), ctx));
    }
}
