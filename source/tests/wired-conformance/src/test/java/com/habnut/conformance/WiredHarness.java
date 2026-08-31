package com.habnut.conformance;

import com.habnut.emulator.wired.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared fixture for the conformance suite.
 *
 * Builds a registry with every wired definition registered, backed by an
 * in-memory variable store and a live signal bus, so wired semantics can be
 * exercised without a database or a running server.
 */
final class WiredHarness {

    final WiredRegistry      registry  = new WiredRegistry();
    final WiredVariableStore variables = new WiredVariableStore(null);
    final WiredSignalBus     signalBus = new WiredSignalBus();
    final WiredExecutor      executor;

    /** Room and actor identifiers used consistently across the suite. */
    static final long ROOM_ID = 100L;
    static final long OTHER_ROOM_ID = 200L;

    WiredHarness() {
        WiredDefinitions.registerTriggers(registry, null);
        WiredDefinitions.registerConditions(registry);
        WiredDefinitions.registerSelectors(registry);
        WiredDefinitions.registerActions(registry);
        // A debugger with no watchers emits nothing, so its collaborators are
        // never dereferenced during a conformance run.
        this.executor = new WiredExecutor(registry, new WiredDebugger(null, null));
    }

    /** A context with no triggering entity, for room- and global-scope work. */
    WiredContext context() {
        return context(ROOM_ID);
    }

    WiredContext context(long roomId) {
        return new WiredContext(roomId, null, null, variables, signalBus);
    }

    /** Builds a component of the given type with alternating key/value params. */
    static WiredStack.WiredComponent component(WiredStack.ComponentType type,
                                               String code, Object... params) {
        return new WiredStack.WiredComponent(1L, type, code, params(params));
    }

    static WiredStack.WiredComponent condition(String code, Object... params) {
        return component(WiredStack.ComponentType.CONDITION, code, params);
    }

    static WiredStack.WiredComponent action(String code, Object... params) {
        return component(WiredStack.ComponentType.ACTION, code, params);
    }

    static WiredStack.WiredComponent trigger(String code, Object... params) {
        return component(WiredStack.ComponentType.TRIGGER, code, params);
    }

    static WiredStack.WiredComponent selector(String code, Object... params) {
        return component(WiredStack.ComponentType.SELECTOR, code, params);
    }

    /** Turns an alternating key/value varargs list into a parameter map. */
    static Map<String, Object> params(Object... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("params requires alternating keys and values");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    /** Evaluates a condition against a fresh context. */
    boolean evaluate(WiredStack.WiredComponent cond, WiredContext ctx) {
        WiredRegistry.ConditionEvaluator eval = registry.getCondition(cond.definitionCode());
        if (eval == null) throw new AssertionError("condition not registered: " + cond.definitionCode());
        return eval.evaluate(cond, ctx);
    }

    /** Executes an action against the given context. */
    void execute(WiredStack.WiredComponent act, WiredContext ctx) {
        WiredRegistry.ActionExecutor exec = registry.getAction(act.definitionCode());
        if (exec == null) throw new AssertionError("action not registered: " + act.definitionCode());
        exec.execute(act, ctx);
    }

    /** Fires a trigger against the given context. */
    boolean fires(WiredStack stack, WiredContext ctx) {
        WiredRegistry.TriggerHandler h = registry.getTrigger(stack.getTrigger().definitionCode());
        if (h == null) throw new AssertionError("trigger not registered: " + stack.getTrigger().definitionCode());
        return h.fires(stack, ctx);
    }

    /** A stack carrying only the given trigger. */
    static WiredStack stackWith(WiredStack.WiredComponent trig) {
        WiredStack s = new WiredStack(1L, ROOM_ID);
        s.setTrigger(trig);
        return s;
    }
}
