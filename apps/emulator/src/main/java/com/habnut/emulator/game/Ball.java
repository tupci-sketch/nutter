package com.habnut.emulator.game;

public final class Ball {

    private double x;
    private double y;
    private double vx;
    private double vy;

    private static final double FRICTION     = 0.85;
    private static final double KICK_SPEED   = 8.0;
    private static final double STOP_EPSILON = 0.05;

    public Ball(double startX, double startY) {
        this.x  = startX;
        this.y  = startY;
        this.vx = 0;
        this.vy = 0;
    }

    public void kick(long kickerId, double fromX, double fromY, double targetX, double targetY) {
        double dx = targetX - fromX;
        double dy = targetY - fromY;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001) {
            // Random direction if exactly on top
            dx = Math.random() * 2 - 1; dy = Math.random() * 2 - 1;
            len = Math.sqrt(dx * dx + dy * dy);
        }
        vx = (dx / len) * KICK_SPEED;
        vy = (dy / len) * KICK_SPEED;
    }

    public void tick(double minX, double minY, double maxX, double maxY) {
        x += vx;
        y += vy;

        // Bounce off walls
        if (x < minX) { x = minX; vx = -vx * 0.7; }
        if (x > maxX) { x = maxX; vx = -vx * 0.7; }
        if (y < minY) { y = minY; vy = -vy * 0.7; }
        if (y > maxY) { y = maxY; vy = -vy * 0.7; }

        // Apply friction
        vx *= FRICTION;
        vy *= FRICTION;

        if (Math.abs(vx) < STOP_EPSILON) vx = 0;
        if (Math.abs(vy) < STOP_EPSILON) vy = 0;
    }

    public boolean isMoving() { return vx != 0 || vy != 0; }

    public void reset(double startX, double startY) {
        x = startX; y = startY; vx = 0; vy = 0;
    }

    public double getX()  { return x; }
    public double getY()  { return y; }
    public double getVx() { return vx; }
    public double getVy() { return vy; }

    public int getTileX() { return (int) Math.round(x); }
    public int getTileY() { return (int) Math.round(y); }
}
