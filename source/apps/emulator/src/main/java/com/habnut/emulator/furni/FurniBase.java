package com.habnut.emulator.furni;

public record FurniBase(
    long id,
    String spriteId,
    String name,
    String description,
    String type,         // "floor" or "wall"
    int width,
    int length,
    double stackHeight,
    boolean canSit,
    boolean canLay,
    boolean canWalk,
    boolean canStack,
    int interactionModes,
    String interactionType,
    int credits,
    int diamonds
) {}
