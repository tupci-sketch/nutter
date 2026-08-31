package com.habnut.emulator.game;

import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.SessionRegistry;
import com.habnut.emulator.protocol.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MatchmakingQueue {

    private static final Logger log = LoggerFactory.getLogger(MatchmakingQueue.class);
    private static final int MIN_PLAYERS_PER_TEAM = 1;
    private static final int MAX_PLAYERS_PER_TEAM = 5;
    private static final int MATCH_TRIGGER_SIZE   = 2; // players needed to start a match

    // queue per game type: userId → preferred team
    private final ConcurrentHashMap<String, List<QueueEntry>> queues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> userInQueue = new ConcurrentHashMap<>();

    private final GameEngine      gameEngine;
    private final SessionRegistry sessionRegistry;
    private final PacketRouter    router;

    public MatchmakingQueue(GameEngine gameEngine, SessionRegistry sessionRegistry, PacketRouter router) {
        this.gameEngine      = gameEngine;
        this.sessionRegistry = sessionRegistry;
        this.router          = router;
    }

    public record QueueEntry(long userId, String preferredTeam, long queuedAt) {}

    public enum EnqueueResult { QUEUED, ALREADY_IN_QUEUE, INVALID_GAME_TYPE }

    public EnqueueResult enqueue(long userId, String gameType, String preferredTeam) {
        if (!isValidGameType(gameType)) return EnqueueResult.INVALID_GAME_TYPE;
        if (userInQueue.containsKey(userId)) return EnqueueResult.ALREADY_IN_QUEUE;

        userInQueue.put(userId, gameType);
        List<QueueEntry> queue = queues.computeIfAbsent(gameType, k -> new CopyOnWriteArrayList<>());
        queue.add(new QueueEntry(userId, preferredTeam, System.currentTimeMillis()));

        log.debug("User {} queued for {} (preferred team: {})", userId, gameType, preferredTeam);

        sessionRegistry.byUserId(userId).ifPresent(s ->
            s.send(router.buildPacket(PacketType.GAME_MATCHMAKING_QUEUED, Map.of("gameType", gameType))));

        tryMatch(gameType);
        return EnqueueResult.QUEUED;
    }

    public boolean dequeue(long userId) {
        String gameType = userInQueue.remove(userId);
        if (gameType == null) return false;
        List<QueueEntry> queue = queues.get(gameType);
        if (queue != null) queue.removeIf(e -> e.userId() == userId);
        return true;
    }

    private void tryMatch(String gameType) {
        List<QueueEntry> queue = queues.get(gameType);
        if (queue == null || queue.size() < MATCH_TRIGGER_SIZE) return;

        // Take up to MATCH_TRIGGER_SIZE players
        List<QueueEntry> matched = new ArrayList<>();
        Iterator<QueueEntry> it = queue.iterator();
        while (it.hasNext() && matched.size() < MATCH_TRIGGER_SIZE) {
            matched.add(it.next());
        }
        queue.removeAll(matched);
        matched.forEach(e -> userInQueue.remove(e.userId()));

        // Assign teams: alternate or honour preferences
        List<Long> redTeam  = new ArrayList<>();
        List<Long> blueTeam = new ArrayList<>();
        for (int i = 0; i < matched.size(); i++) {
            QueueEntry e = matched.get(i);
            if ("blue".equals(e.preferredTeam()) && blueTeam.size() < MAX_PLAYERS_PER_TEAM) {
                blueTeam.add(e.userId());
            } else {
                redTeam.add(e.userId());
            }
        }

        // Use room 0 as a placeholder lobby room — GameEngine allocates room context
        GameMatch match = gameEngine.createMatch(gameType, 0L);
        redTeam.forEach(uid  -> match.addPlayer(uid, "red"));
        blueTeam.forEach(uid -> match.addPlayer(uid, "blue"));

        Map<String, Object> payload = Map.of(
            "matchId",   match.getMatchId(),
            "gameType",  gameType,
            "redTeam",   redTeam,
            "blueTeam",  blueTeam
        );
        String matchedJson = router.buildPacket(PacketType.GAME_MATCHMAKING_MATCHED, payload);
        matched.forEach(e -> sessionRegistry.byUserId(e.userId()).ifPresent(s -> s.send(matchedJson)));

        match.start();
        log.info("Matched {} players for {} match {}", matched.size(), gameType, match.getMatchId());
    }

    private boolean isValidGameType(String t) {
        return Set.of("football", "battleball", "freeze", "racing", "telephrase").contains(t);
    }
}
