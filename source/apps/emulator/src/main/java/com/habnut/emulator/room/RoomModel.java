package com.habnut.emulator.room;

public record RoomModel(
    String id,
    String heightmap,
    int doorX,
    int doorY,
    int doorRotation,
    int maxVisitors
) {
    public NavigationGrid buildGrid() {
        return NavigationGrid.fromModel(heightmap);
    }
}
