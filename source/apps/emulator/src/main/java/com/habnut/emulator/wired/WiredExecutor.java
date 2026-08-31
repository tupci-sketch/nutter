package com.habnut.emulator.wired;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class WiredExecutor {

    private static final Logger log = LoggerFactory.getLogger(WiredExecutor.class);

    private final WiredRegistry registry;
    private final WiredDebugger debugger;

    public WiredExecutor(WiredRegistry registry, WiredDebugger debugger) {
        this.registry = registry;
        this.debugger = debugger;
    }

    public boolean execute(WiredStack stack, WiredContext ctx) {
        if (stack.getTrigger() == null) return false;
        ctx.tick();

        // Evaluate conditions — all must pass (AND semantics)
        for (WiredStack.WiredComponent cond : stack.getConditions()) {
            WiredRegistry.ConditionEvaluator eval = registry.getCondition(cond.definitionCode());
            if (eval == null) {
                log.warn("Unknown condition: {}", cond.definitionCode()); return false;
            }
            ctx.tick();
            boolean result;
            try { result = eval.evaluate(cond, ctx); }
            catch (WiredExecutionLimitException e) { throw e; }
            catch (Exception e) {
                log.warn("Condition threw: code={}", cond.definitionCode(), e);
                return false;
            }
            debugger.logStep(ctx.roomId, "CONDITION", cond.definitionCode(), result);
            if (!result) return false;
        }

        // Resolve selectors — collect furni ids to act on
        List<Long> targets = null;
        for (WiredStack.WiredComponent sel : stack.getSelectors()) {
            WiredRegistry.SelectorEvaluator eval = registry.getSelector(sel.definitionCode());
            if (eval == null) {
                log.warn("Unknown selector: {}", sel.definitionCode()); continue;
            }
            ctx.tick();
            try { targets = eval.select(sel, ctx); }
            catch (Exception e) { log.warn("Selector threw: code={}", sel.definitionCode(), e); }
        }
        if (targets != null) ctx.locals.put("selectedFurnis", targets);

        // Execute actions
        for (WiredStack.WiredComponent action : stack.getActions()) {
            WiredRegistry.ActionExecutor exec = registry.getAction(action.definitionCode());
            if (exec == null) {
                log.warn("Unknown action: {}", action.definitionCode()); continue;
            }
            ctx.tick();
            try {
                exec.execute(action, ctx);
                debugger.logStep(ctx.roomId, "ACTION", action.definitionCode(), true);
            } catch (WiredExecutionLimitException e) { throw e; }
            catch (Exception e) {
                log.warn("Action threw: code={}", action.definitionCode(), e);
                debugger.logStep(ctx.roomId, "ACTION", action.definitionCode(), false);
            }
        }
        return true;
    }
}
