package com.habnut.emulator.wired;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WiredStack {

    public enum ComponentType { TRIGGER, ACTION, CONDITION, SELECTOR }

    public record WiredComponent(long furniId, ComponentType componentType,
                                 String definitionCode, Map<String, Object> params) {}

    private final long stackId;
    private final long roomId;
    private volatile WiredComponent trigger;
    private final List<WiredComponent> conditions = new CopyOnWriteArrayList<>();
    private final List<WiredComponent> selectors  = new CopyOnWriteArrayList<>();
    private final List<WiredComponent> actions    = new CopyOnWriteArrayList<>();

    public WiredStack(long stackId, long roomId) {
        this.stackId = stackId;
        this.roomId  = roomId;
    }

    public long getStackId() { return stackId; }
    public long getRoomId()  { return roomId; }

    public void setTrigger(WiredComponent trigger) { this.trigger = trigger; }
    public WiredComponent getTrigger() { return trigger; }

    public void addCondition(WiredComponent c) { conditions.add(c); }
    public void addSelector(WiredComponent s)  { selectors.add(s); }
    public void addAction(WiredComponent a)    { actions.add(a); }

    public void clearConditions() { conditions.clear(); }
    public void clearSelectors()  { selectors.clear(); }
    public void clearActions()    { actions.clear(); }

    public List<WiredComponent> getConditions() { return Collections.unmodifiableList(conditions); }
    public List<WiredComponent> getSelectors()  { return Collections.unmodifiableList(selectors); }
    public List<WiredComponent> getActions()    { return Collections.unmodifiableList(actions); }
}
