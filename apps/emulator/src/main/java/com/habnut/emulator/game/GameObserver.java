package com.habnut.emulator.game;

import java.util.Map;

public interface GameObserver {
    void onStateChange(long matchId, GameState newState);
    void onScoreUpdate(long matchId, Map<String, Integer> scores);
    void onMatchEvent(long matchId, String eventType, Map<String, Object> data);
    void onMatchEnd(long matchId, Map<String, Object> results);
}
