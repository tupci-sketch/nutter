package com.habnut.emulator.furni;

import java.util.concurrent.atomic.AtomicInteger;

public final class WallItem {

    public final long id;
    public final long ownerId;
    public final FurniBase base;

    private volatile String wallPosition;
    private final AtomicInteger state = new AtomicInteger(0);
    private volatile String extraData = "";

    public WallItem(long id, long ownerId, FurniBase base,
                    String wallPosition, String extraData) {
        this.id           = id;
        this.ownerId      = ownerId;
        this.base         = base;
        this.wallPosition = wallPosition != null ? wallPosition : ":w=0,0 l=0,0 l";
        this.extraData    = extraData != null ? extraData : "";
    }

    public String getWallPosition() { return wallPosition; }
    public int getState()            { return state.get(); }
    public String getExtra()         { return extraData; }

    public void setWallPosition(String pos) { wallPosition = pos; }
    public void setState(int s)              { state.set(s); }
    public void setExtra(String e)           { extraData = e; }

    public int nextState() {
        int modes = base.interactionModes();
        if (modes <= 1) return 0;
        int next = (state.get() + 1) % modes;
        state.set(next);
        return next;
    }
}
