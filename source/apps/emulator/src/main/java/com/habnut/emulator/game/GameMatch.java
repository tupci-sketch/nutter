package com.habnut.emulator.game;

import java.util.List;
import java.util.Map;

public interface GameMatch {
    long getMatchId();
    String getGameType();
    GameState getState();
    long getRoomId();

    void addPlayer(long userId, String team);
    void removePlayer(long userId);
    boolean hasPlayer(long userId);
    List<Long> getPlayers();

    void start();
    void end();
    void cancel();

    void onTick();
    void onPlayerInput(long userId, String inputType, Map<String, Object> data);

    Map<String, Integer> getScores();
    Map<String, Object> getResults();

    void setObserver(GameObserver observer);
}
