package com.habnut.emulator.room;

public record Position(int x, int y, double z, int rotation) {

    public static final int ROT_N  = 0;
    public static final int ROT_NE = 2;
    public static final int ROT_E  = 4;
    public static final int ROT_SE = 6;

    public Position withZ(double newZ) {
        return new Position(x, y, newZ, rotation);
    }

    public Position withRotation(int newRot) {
        return new Position(x, y, z, newRot);
    }

    public double distanceTo(Position other) {
        int dx = x - other.x;
        int dy = y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public boolean sameXY(Position other) {
        return x == other.x && y == other.y;
    }

    public int rotationTo(Position target) {
        int dx = target.x - x;
        int dy = target.y - y;
        if (dx == 0 && dy < 0)  return 0;
        if (dx > 0  && dy < 0)  return 2;
        if (dx > 0  && dy == 0) return 4;
        if (dx > 0  && dy > 0)  return 6;
        if (dx == 0 && dy > 0)  return 4; // South mapped to E visually
        if (dx < 0  && dy > 0)  return 6;
        if (dx < 0  && dy == 0) return 0;
        if (dx < 0  && dy < 0)  return 6;
        return rotation;
    }

    @Override
    public String toString() {
        return String.format("(%d,%d,%.1f,r%d)", x, y, z, rotation);
    }
}
