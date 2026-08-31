package com.habnut.emulator.room;

import java.util.*;

public final class NavigationGrid {

    public enum Tile { OPEN, BLOCKED, DOOR }

    private final Tile[][] grid;
    private final double[][] heights;
    private final int width;
    private final int height;

    public NavigationGrid(int width, int height) {
        this.width  = width;
        this.height = height;
        this.grid    = new Tile[width][height];
        this.heights = new double[width][height];
        for (Tile[] row : grid) Arrays.fill(row, Tile.OPEN);
    }

    public static NavigationGrid fromModel(String heightmap) {
        String[] rows = heightmap.split("\r?\n");
        int h = rows.length;
        int w = rows[0].length();
        NavigationGrid nav = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) {
            String row = rows[y];
            for (int x = 0; x < Math.min(w, row.length()); x++) {
                char c = row.charAt(x);
                if (c == 'x' || c == 'X') {
                    nav.grid[x][y] = Tile.BLOCKED;
                } else if (c >= '0' && c <= '9') {
                    nav.heights[x][y] = c - '0';
                } else if (c >= 'a' && c <= 'z') {
                    nav.heights[x][y] = 10 + (c - 'a');
                }
            }
        }
        return nav;
    }

    public boolean isWalkable(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return false;
        return grid[x][y] != Tile.BLOCKED;
    }

    public double getHeight(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0;
        return heights[x][y];
    }

    public void setBlocked(int x, int y, boolean blocked) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            grid[x][y] = blocked ? Tile.BLOCKED : Tile.OPEN;
        }
    }

    public int getWidth()  { return width; }
    public int getHeight() { return height; }

    public List<int[]> findPath(int sx, int sy, int ex, int ey) {
        if (!isWalkable(ex, ey)) return Collections.emptyList();

        int[][] dist = new int[width][height];
        for (int[] row : dist) Arrays.fill(row, Integer.MAX_VALUE);
        dist[sx][sy] = 0;

        int[][] prev = new int[width * height][2];
        for (int[] p : prev) { p[0] = -1; p[1] = -1; }

        PriorityQueue<int[]> open = new PriorityQueue<>(Comparator.comparingInt(a -> a[2]));
        open.add(new int[]{sx, sy, heuristic(sx, sy, ex, ey)});

        int[][] dirs = {{0,-1},{1,-1},{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1}};

        while (!open.isEmpty()) {
            int[] cur = open.poll();
            int cx = cur[0], cy = cur[1];

            if (cx == ex && cy == ey) return reconstructPath(prev, sx, sy, ex, ey);

            for (int[] d : dirs) {
                int nx = cx + d[0], ny = cy + d[1];
                if (!isWalkable(nx, ny)) continue;
                int cost = dist[cx][cy] + (d[0] != 0 && d[1] != 0 ? 14 : 10);
                if (cost < dist[nx][ny]) {
                    dist[nx][ny] = cost;
                    prev[nx * height + ny][0] = cx;
                    prev[nx * height + ny][1] = cy;
                    open.add(new int[]{nx, ny, cost + heuristic(nx, ny, ex, ey)});
                }
            }
        }
        return Collections.emptyList();
    }

    private int heuristic(int x1, int y1, int x2, int y2) {
        return Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2)) * 10;
    }

    private List<int[]> reconstructPath(int[][] prev, int sx, int sy, int ex, int ey) {
        LinkedList<int[]> path = new LinkedList<>();
        int cx = ex, cy = ey;
        while (cx != sx || cy != sy) {
            path.addFirst(new int[]{cx, cy});
            int[] p = prev[cx * height + cy];
            if (p[0] < 0) break;
            cx = p[0]; cy = p[1];
        }
        return path;
    }
}
