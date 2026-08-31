package com.habnut.emulator.wired;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public final class WiredRegistry {

    @FunctionalInterface public interface TriggerHandler { boolean fires(WiredStack stack, WiredContext ctx); }
    @FunctionalInterface public interface ConditionEvaluator { boolean evaluate(WiredStack.WiredComponent c, WiredContext ctx); }
    @FunctionalInterface public interface SelectorEvaluator  { java.util.List<Long> select(WiredStack.WiredComponent c, WiredContext ctx); }
    @FunctionalInterface public interface ActionExecutor     { void execute(WiredStack.WiredComponent c, WiredContext ctx); }

    private final Map<String, TriggerHandler>    triggers   = new ConcurrentHashMap<>();
    private final Map<String, ConditionEvaluator> conditions = new ConcurrentHashMap<>();
    private final Map<String, SelectorEvaluator>  selectors  = new ConcurrentHashMap<>();
    private final Map<String, ActionExecutor>      actions    = new ConcurrentHashMap<>();

    public void registerTrigger(String code, TriggerHandler h)   { triggers.put(code, h); }
    public void registerCondition(String code, ConditionEvaluator e) { conditions.put(code, e); }
    public void registerSelector(String code, SelectorEvaluator s)   { selectors.put(code, s); }
    public void registerAction(String code, ActionExecutor e)        { actions.put(code, e); }

    public TriggerHandler    getTrigger(String code)   { return triggers.get(code); }
    public ConditionEvaluator getCondition(String code) { return conditions.get(code); }
    public SelectorEvaluator  getSelector(String code)  { return selectors.get(code); }
    public ActionExecutor     getAction(String code)    { return actions.get(code); }

    public boolean hasTrigger(String code)   { return triggers.containsKey(code); }
    public boolean hasCondition(String code) { return conditions.containsKey(code); }
    public boolean hasSelector(String code)  { return selectors.containsKey(code); }
    public boolean hasAction(String code)    { return actions.containsKey(code); }
}
