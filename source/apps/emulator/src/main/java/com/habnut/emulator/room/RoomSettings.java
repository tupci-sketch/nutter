package com.habnut.emulator.room;

public record RoomSettings(
    long id,
    long ownerId,
    String ownerName,
    String name,
    String description,
    String modelId,
    // The two surfaces a player picks when they redecorate. Both were already
    // being saved; neither was ever read back, so a redecorated room came back
    // plain the next time anyone walked in.
    String wallpaper,
    String floorPattern,
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
