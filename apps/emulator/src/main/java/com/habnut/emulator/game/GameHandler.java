package com.habnut.emulator.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.SessionRegistry;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.protocol.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;

public final class GameHandler {

    private static final Logger log = LoggerFactory.getLogger(GameHandler.class);

    private final GameEngine        gameEngine;
    private final TournamentService tournamentService;
    private final MatchmakingQueue  matchmakingQueue;
    private final SessionRegistry   sessionRegistry;
    private final PacketRouter      router;

    public GameHandler(GameEngine gameEngine, TournamentService tournamentService,
                       MatchmakingQueue matchmakingQueue, SessionRegistry sessionRegistry,
                       PacketRouter router) {
        this.gameEngine        = gameEngine;
        this.tournamentService = tournamentService;
        this.matchmakingQueue  = matchmakingQueue;
        this.sessionRegistry   = sessionRegistry;
        this.router            = router;
    }

    public void register(PacketRouter router) {
        router.register(PacketType.GAME_JOIN,            this::handleJoin);
        router.register(PacketType.GAME_LEAVE,           this::handleLeave);
        router.register(PacketType.GAME_READY,           this::handleReady);
        router.register(PacketType.GAME_INPUT,           this::handleInput);
        router.register(PacketType.GAME_FOOTBALL_KICK,   this::handleFootballKick);
        router.register(PacketType.GAME_BATTLEBALL_JUMP, this::handleBattleballJump);
        router.register(PacketType.GAME_FREEZE_THROW,    this::handleFreezeThrow);
        router.register(PacketType.GAME_MATCHMAKING_QUEUED,  this::handleMatchmakingQueue);
        router.register(PacketType.GAME_MATCHMAKING_CANCEL,  this::handleMatchmakingCancel);
        router.register(PacketType.GAME_LEADERBOARD,     this::handleLeaderboard);
        router.register(PacketType.TRN_LIST,             this::handleTournamentList);
        router.register(PacketType.TRN_INFO,             this::handleTournamentInfo);
        router.register(PacketType.TRN_REGISTER,         this::handleTournamentRegister);
        router.register(PacketType.TRN_BRACKET,          this::handleTournamentBracket);
    }

    // --- In-match ---

    private void handleJoin(WebSocketSession session, JsonNode p) {
        long matchId = p.path("matchId").asLong(-1);
        String team  = p.path("team").asText("red");
        gameEngine.getMatch(matchId).ifPresentOrElse(
            match -> {
                match.addPlayer(session.getUserId(), team);
                session.send(router.buildPacket(PacketType.GAME_LOBBY_STATE, Map.of(
                    "matchId", matchId, "gameType", match.getGameType(),
                    "state", match.getState().name(), "scores", match.getScores())));
            },
            () -> sendError(session, "match_not_found"));
    }

    private void handleLeave(WebSocketSession session, JsonNode p) {
        long matchId = p.path("matchId").asLong(-1);
        gameEngine.getMatch(matchId).ifPresent(m -> m.removePlayer(session.getUserId()));
    }

    private void handleReady(WebSocketSession session, JsonNode p) {
        long matchId = p.path("matchId").asLong(-1);
        gameEngine.getMatch(matchId).ifPresent(m -> {
            if (m.getState() == GameState.WAITING) m.start();
        });
    }

    private void handleInput(WebSocketSession session, JsonNode p) {
        long matchId    = p.path("matchId").asLong(-1);
        String inputType = p.path("inputType").asText("");
        gameEngine.getMatch(matchId).ifPresent(m ->
            m.onPlayerInput(session.getUserId(), inputType, jsonToMap(p)));
    }

    private void handleFootballKick(WebSocketSession session, JsonNode p) {
        long roomId = p.path("roomId").asLong(-1);
        gameEngine.getMatchForRoom(roomId).ifPresent(m ->
            m.onPlayerInput(session.getUserId(), "kick", jsonToMap(p)));
    }

    private void handleBattleballJump(WebSocketSession session, JsonNode p) {
        long roomId = p.path("roomId").asLong(-1);
        gameEngine.getMatchForRoom(roomId).ifPresent(m ->
            m.onPlayerInput(session.getUserId(), "jump", jsonToMap(p)));
    }

    private void handleFreezeThrow(WebSocketSession session, JsonNode p) {
        long roomId = p.path("roomId").asLong(-1);
        gameEngine.getMatchForRoom(roomId).ifPresent(m ->
            m.onPlayerInput(session.getUserId(), "throw", jsonToMap(p)));
    }

    // --- Matchmaking ---

    private void handleMatchmakingQueue(WebSocketSession session, JsonNode p) {
        String gameType      = p.path("gameType").asText("");
        String preferredTeam = p.path("team").asText("any");
        MatchmakingQueue.EnqueueResult result =
            matchmakingQueue.enqueue(session.getUserId(), gameType, preferredTeam);
        if (result != MatchmakingQueue.EnqueueResult.QUEUED)
            sendError(session, result.name().toLowerCase());
    }

    private void handleMatchmakingCancel(WebSocketSession session, JsonNode p) {
        matchmakingQueue.dequeue(session.getUserId());
    }

    // --- Leaderboard ---

    private void handleLeaderboard(WebSocketSession session, JsonNode p) {
        String gameType = p.path("gameType").asText("football");
        session.send(router.buildPacket(PacketType.GAME_LEADERBOARD,
            Map.of("gameType", gameType, "rows", List.of())));
    }

    // --- Tournament ---

    private void handleTournamentList(WebSocketSession session, JsonNode p) {
        try {
            List<TournamentService.Tournament> list = tournamentService.listOpen();
            session.send(router.buildPacket(PacketType.TRN_LIST_RESULT,
                Map.of("tournaments", serializeTournaments(list))));
        } catch (SQLException e) {
            log.error("Tournament list error", e);
            sendError(session, "server_error");
        }
    }

    private void handleTournamentInfo(WebSocketSession session, JsonNode p) {
        long id = p.path("tournamentId").asLong(-1);
        try {
            tournamentService.findById(id).ifPresentOrElse(
                t -> session.send(router.buildPacket(PacketType.TRN_INFO, serializeTournament(t))),
                () -> sendError(session, "not_found"));
        } catch (SQLException e) {
            log.error("Tournament info error", e);
            sendError(session, "server_error");
        }
    }

    private void handleTournamentRegister(WebSocketSession session, JsonNode p) {
        long   id       = p.path("tournamentId").asLong(-1);
        String teamName = p.path("teamName").asText("Team " + session.getUserId());
        try {
            TournamentService.RegisterResult result =
                tournamentService.register(id, session.getUserId(), teamName);
            if (result == TournamentService.RegisterResult.REGISTERED) {
                session.send(router.buildPacket(PacketType.TRN_REGISTERED,
                    Map.of("tournamentId", id, "teamName", teamName)));
            } else {
                sendError(session, result.name().toLowerCase());
            }
        } catch (SQLException e) {
            log.error("Tournament register error", e);
            sendError(session, "server_error");
        }
    }

    private void handleTournamentBracket(WebSocketSession session, JsonNode p) {
        long id = p.path("tournamentId").asLong(-1);
        try {
            List<TournamentService.BracketMatch> bracket = tournamentService.getBracket(id);
            session.send(router.buildPacket(PacketType.TRN_BRACKET_RESULT,
                Map.of("tournamentId", id, "bracket", serializeBracket(bracket))));
        } catch (SQLException e) {
            log.error("Tournament bracket error", e);
            sendError(session, "server_error");
        }
    }

    // --- Serialization ---

    private List<Map<String, Object>> serializeTournaments(List<TournamentService.Tournament> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var t : list) out.add(serializeTournament(t));
        return out;
    }

    private Map<String, Object> serializeTournament(TournamentService.Tournament t) {
        return Map.of("id", t.id(), "name", t.name(), "gameType", t.gameType(),
            "status", t.status(), "maxTeams", t.maxTeams(),
            "currentTeams", t.currentTeams(), "startAt", t.startAt());
    }

    private List<Map<String, Object>> serializeBracket(List<TournamentService.BracketMatch> bracket) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var m : bracket) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", m.id()); row.put("round", m.round());
            row.put("matchNumber", m.matchNumber());
            if (m.teamAId()  != null) row.put("teamAId",  m.teamAId());
            if (m.teamBId()  != null) row.put("teamBId",  m.teamBId());
            if (m.winnerId() != null) row.put("winnerId", m.winnerId());
            row.put("status", m.status());
            out.add(row);
        }
        return out;
    }

    private Map<String, Object> jsonToMap(JsonNode node) {
        Map<String, Object> map = new HashMap<>();
        node.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            if (v.isLong())    map.put(e.getKey(), v.asLong());
            else if (v.isInt()) map.put(e.getKey(), v.asInt());
            else if (v.isDouble()) map.put(e.getKey(), v.asDouble());
            else if (v.isBoolean()) map.put(e.getKey(), v.asBoolean());
            else map.put(e.getKey(), v.asText());
        });
        return map;
    }

    private void sendError(WebSocketSession session, String reason) {
        session.send(router.buildPacket("game.error", Map.of("reason", reason)));
    }
}
