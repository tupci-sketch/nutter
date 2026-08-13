package com.habnut.emulator.game;

public enum GameState {
    WAITING,    // Lobby: accepting players
    COUNTDOWN,  // 10-second countdown before start
    RUNNING,    // Active play
    ENDED,      // Game over, results displayed
    CANCELLED   // Abandoned before completion
}
