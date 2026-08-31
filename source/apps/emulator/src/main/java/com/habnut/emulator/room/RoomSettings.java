package com.habnut.emulator.room;

public record RoomSettings(
    long id,
    long ownerId,
    String ownerName,
    String name,
    String description,
    String modelId,
    int accessType,        // 0=open 1=doorbell 2=password 3=invisible
    String passwordHash,
    int maxVisitors,
    boolean allowPets,
    boolean allowPetsEat,
    boolean allowWalkthrough,
    boolean hideWalls,
    int wallHeight,
    String floorThickness,
    String wallThickness,
    String backgroundColour,
    String landscapeColour,
    int score,
    boolean promoted,
    String category
) {}
