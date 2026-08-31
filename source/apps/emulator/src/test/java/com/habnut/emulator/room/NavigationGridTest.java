package com.habnut.emulator.room;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NavigationGridTest {

    @Test
    void parsesHeightmap() {
        String map = "1111\n1xx1\n1111";
        NavigationGrid grid = NavigationGrid.fromModel(map);
        assertEquals(4, grid.getWidth());
        assertEquals(3, grid.getHeight());
        assertTrue(grid.isWalkable(0, 0));
        assertFalse(grid.isWalkable(1, 1));
        assertFalse(grid.isWalkable(2, 1));
        assertTrue(grid.isWalkable(3, 0));
    }

    @Test
    void findsPathAroundObstacle() {
        // 5x3 grid with a wall column at x=2
        String map = "11111\n11x11\n11x11";
        NavigationGrid grid = NavigationGrid.fromModel(map);
        List<int[]> path = grid.findPath(0, 0, 4, 0);
        assertFalse(path.isEmpty());
        int[] last = path.get(path.size() - 1);
        assertEquals(4, last[0]);
        assertEquals(0, last[1]);
    }

    @Test
    void returnsEmptyPathToBlockedTile() {
        NavigationGrid grid = NavigationGrid.fromModel("1x1");
        List<int[]> path = grid.findPath(0, 0, 1, 0);
        assertTrue(path.isEmpty());
    }

    @Test
    void directPath() {
        NavigationGrid grid = NavigationGrid.fromModel("11111");
        List<int[]> path = grid.findPath(0, 0, 4, 0);
        assertFalse(path.isEmpty());
        assertEquals(4, path.get(path.size() - 1)[0]);
    }

    @Test
    void heightIsReadFromMap() {
        NavigationGrid grid = NavigationGrid.fromModel("0123");
        assertEquals(0.0, grid.getHeight(0, 0));
        assertEquals(1.0, grid.getHeight(1, 0));
        assertEquals(2.0, grid.getHeight(2, 0));
        assertEquals(3.0, grid.getHeight(3, 0));
    }
}
