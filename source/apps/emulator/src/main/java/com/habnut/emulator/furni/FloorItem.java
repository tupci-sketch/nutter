package com.habnut.emulator.furni;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class FloorItem {

    public final long id;
    public final long ownerId;
    public final FurniBase base;

    private final AtomicInteger x        = new AtomicInteger();
    private final AtomicInteger y        = new AtomicInteger();
    private final AtomicReference<Double> z = new AtomicReference<>(0.0);
    private final AtomicInteger rotation = new AtomicInteger();
    private final AtomicInteger state    = new AtomicInteger(0);
    private volatile String extraData    = "";

    public FloorItem(long id, long ownerId, FurniBase base,
                     int x, int y, double z, int rotation, String extraData) {
        this.id      = id;
        this.ownerId = ownerId;
        this.base    = base;
        this.x.set(x);
        this.y.set(y);
        this.z.set(z);
        this.rotation.set(rotation);
        this.extraData = extraData != null ? extraData : "";
    }

    public int getX()         { return x.get(); }
    public int getY()         { return y.get(); }
    public double getZ()      { return z.get(); }
    public int getRotation()  { return rotation.get(); }
    public int getState()     { return state.get(); }
    public String getExtra()  { return extraData; }

    public void setPosition(int nx, int ny, double nz, int rot) {
        x.set(nx); y.set(ny); z.set(nz); rotation.set(rot);
    }

    public void setState(int s) { state.set(s); }
    public void setExtra(String e) { extraData = e; }

    public int nextState() {
        int modes = base.interactionModes();
        if (modes <= 1) return 0;
        int next = (state.get() + 1) % modes;
        state.set(next);
        return next;
    }
}
