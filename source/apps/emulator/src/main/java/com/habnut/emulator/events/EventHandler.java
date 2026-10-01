package com.habnut.emulator.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.habnut.emulator.auth.UserRepository;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.SessionRegistry;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.protocol.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public final class EventHandler {

    private static final Logger log = LoggerFactory.getLogger(EventHandler.class);

    private final EventService       eventService;
    private final CompetitionService competitionService;
    private final SeasonService      seasonService;
    private final UserRepository     userRepo;
    private final SessionRegistry    sessions;
    private final PacketRouter       router;

    public EventHandler(EventService eventService, CompetitionService competitionService,
                        SeasonService seasonService, UserRepository userRepo,
                        SessionRegistry sessions, PacketRouter router) {
        this.eventService       = eventService;
        this.competitionService = competitionService;
        this.seasonService      = seasonService;
        this.userRepo           = userRepo;
        this.sessions           = sessions;
        this.router             = router;
    }

    public void register(PacketRouter router) {
        router.register(PacketType.EVT_LIST,        this::handleEventList);
        router.register(PacketType.EVT_INFO,        this::handleEventInfo);
        router.register(PacketType.EVT_CREATE,      this::handleEventCreate);
        router.register(PacketType.EVT_JOIN,        this::handleEventJoin);
        router.register(PacketType.EVT_LEAVE,       this::handleEventLeave);

        router.register(PacketType.CMP_LIST,        this::handleCompList);
        router.register(PacketType.CMP_INFO,        this::handleCompInfo);
        router.register(PacketType.CMP_REGISTER,    this::handleCompRegister);
        router.register(PacketType.CMP_SCORE_SUBMIT, this::handleCompScore);
        router.register(PacketType.CMP_LEADERBOARD, this::handleCompLeaderboard);

        router.register(PacketType.SEA_CURRENT,     this::handleSeasonCurrent);
        router.register(PacketType.SEA_PROGRESS,    this::handleSeasonProgress);
        router.register(PacketType.SEA_LEADERBOARD, this::handleSeasonLeaderboard);
        router.register(PacketType.SEA_CLAIM_REWARDS, this::handleSeasonClaimRewards);
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    private void handleEventList(WebSocketSession session, JsonNode p) {
        try {
            List<EventService.Event> events = eventService.listActive();
            session.send(router.buildPacket(PacketType.EVT_LIST_RESULT,
                Map.of("events", events.stream().map(this::eventToMap).collect(Collectors.toList()))));
        } catch (SQLException e) {
            log.error("Event list error", e);
            sendError(session, "server_error");
        }
    }

    private void handleEventInfo(WebSocketSession session, JsonNode p) {
        long eventId = p.path("eventId").asLong(-1);
        try {
            Optional<EventService.Event> opt = eventService.findById(eventId);
            if (opt.isEmpty()) { sendError(session, "not_found"); return; }
            int count = eventService.participantCount(eventId);
            Map<String, Object> payload = new HashMap<>(eventToMap(opt.get()));
            payload.put("participantCount", count);
            session.send(router.buildPacket(PacketType.EVT_INFO_RESULT, payload));
        } catch (SQLException e) {
            log.error("Event info error", e);
            sendError(session, "server_error");
        }
    }

    private void handleEventCreate(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        UserRepository.UserRow user = userRepo.findById(userId);
        if (user == null || user.rank() < 3) { sendError(session, "permission_denied"); return; }

        String name    = p.path("name").asText("");
        String desc    = p.path("description").asText("");
        String type    = p.path("type").asText("social");
        String starts  = p.path("startsAt").asText("");
        String ends    = p.path("endsAt").asText("");
        long roomId    = p.path("roomId").asLong(-1);
        int maxPart    = p.path("maxParticipants").asInt(0);

        if (name.isBlank() || starts.isBlank() || ends.isBlank()) {
            sendError(session, "invalid_payload"); return;
        }
        try {
            long id = eventService.create(userId, name, desc, type, starts, ends,
                roomId > 0 ? roomId : null, maxPart > 0 ? maxPart : null);
            session.send(router.buildPacket(PacketType.EVT_CREATED, Map.of("eventId", id)));
        } catch (SQLException e) {
            log.error("Event create error", e);
            sendError(session, "server_error");
        }
    }

    private void handleEventJoin(WebSocketSession session, JsonNode p) {
        long userId  = session.getUserId();
        long eventId = p.path("eventId").asLong(-1);
        try {
            boolean ok = eventService.join(eventId, userId);
            if (!ok) sendError(session, "join_failed");
        } catch (SQLException e) {
            log.error("Event join error", e);
            sendError(session, "server_error");
        }
    }

    private void handleEventLeave(WebSocketSession session, JsonNode p) {
        long userId  = session.getUserId();
        long eventId = p.path("eventId").asLong(-1);
        try {
            eventService.leave(eventId, userId);
        } catch (SQLException e) {
            log.error("Event leave error", e);
            sendError(session, "server_error");
        }
    }

    // ─── Competitions ─────────────────────────────────────────────────────────

    private void handleCompList(WebSocketSession session, JsonNode p) {
        try {
            List<CompetitionService.Competition> comps = competitionService.listActive();
            session.send(router.buildPacket(PacketType.CMP_LIST_RESULT,
                Map.of("competitions", comps.stream().map(this::compToMap)
                    .collect(Collectors.toList()))));
        } catch (SQLException e) {
            log.error("Competition list error", e);
            sendError(session, "server_error");
        }
    }

    private void handleCompInfo(WebSocketSession session, JsonNode p) {
        long compId = p.path("competitionId").asLong(-1);
        try {
            Optional<CompetitionService.Competition> opt = competitionService.findById(compId);
            if (opt.isEmpty()) { sendError(session, "not_found"); return; }
            session.send(router.buildPacket(PacketType.CMP_INFO_RESULT, compToMap(opt.get())));
        } catch (SQLException e) {
            log.error("Competition info error", e);
            sendError(session, "server_error");
        }
    }

    private void handleCompRegister(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        long compId = p.path("competitionId").asLong(-1);
        try {
            boolean ok = competitionService.register(compId, userId);
            if (!ok) sendError(session, "already_registered");
        } catch (SQLException e) {
            log.error("Competition register error", e);
            sendError(session, "server_error");
        }
    }

    private void handleCompScore(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        long compId = p.path("competitionId").asLong(-1);
        int score   = p.path("score").asInt(0);
        try {
            competitionService.submitScore(compId, userId, score);
        } catch (SQLException e) {
            log.error("Competition score error", e);
            sendError(session, "server_error");
        }
    }

    private void handleCompLeaderboard(WebSocketSession session, JsonNode p) {
        long compId = p.path("competitionId").asLong(-1);
        try {
            List<CompetitionService.Participant> lb = competitionService.getLeaderboard(compId);
            session.send(router.buildPacket(PacketType.CMP_LEADERBOARD_RESULT,
                Map.of("competitionId", compId,
                    "entries", lb.stream().map(pt ->
                        Map.of("userId", pt.userId(), "score", pt.score(),
                            "rank", pt.rank() != null ? pt.rank() : 0))
                        .collect(Collectors.toList()))));
        } catch (SQLException e) {
            log.error("Competition leaderboard error", e);
            sendError(session, "server_error");
        }
    }

    // ─── Seasons ──────────────────────────────────────────────────────────────

    private void handleSeasonCurrent(WebSocketSession session, JsonNode p) {
        try {
            Optional<SeasonService.Season> opt = seasonService.getActive();
            if (opt.isEmpty()) {
                session.send(router.buildPacket(PacketType.SEA_CURRENT_RESULT,
                    Map.of("active", false)));
                return;
            }
            Map<String, Object> payload = new HashMap<>(seasonToMap(opt.get()));
            payload.put("active", true);
            session.send(router.buildPacket(PacketType.SEA_CURRENT_RESULT, payload));
        } catch (SQLException e) {
            log.error("Season current error", e);
            sendError(session, "server_error");
        }
    }

    private void handleSeasonProgress(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        try {
            Optional<SeasonService.Season> activeSeason = seasonService.getActive();
            if (activeSeason.isEmpty()) {
                session.send(router.buildPacket(PacketType.SEA_PROGRESS_RESULT,
                    Map.of("active", false))); return;
            }
            Optional<SeasonService.SeasonProgress> prog =
                seasonService.getProgress(activeSeason.get().id(), userId);
            session.send(router.buildPacket(PacketType.SEA_PROGRESS_RESULT, Map.of(
                "seasonId", activeSeason.get().id(),
                "points",   prog.map(SeasonService.SeasonProgress::points).orElse(0),
                "claimedRewards", prog.map(SeasonService.SeasonProgress::claimedRewards).orElse(false)
            )));
        } catch (SQLException e) {
            log.error("Season progress error", e);
            sendError(session, "server_error");
        }
    }

    private void handleSeasonLeaderboard(WebSocketSession session, JsonNode p) {
        int limit = Math.min(p.path("limit").asInt(25), 100);
        try {
            Optional<SeasonService.Season> activeSeason = seasonService.getActive();
            if (activeSeason.isEmpty()) {
                session.send(router.buildPacket(PacketType.SEA_LEADERBOARD_RESULT,
                    Map.of("active", false))); return;
            }
            List<SeasonService.SeasonProgress> lb =
                seasonService.getLeaderboard(activeSeason.get().id(), limit);
            session.send(router.buildPacket(PacketType.SEA_LEADERBOARD_RESULT, Map.of(
                "seasonId", activeSeason.get().id(),
                "entries",  lb.stream().map(sp ->
                    Map.of("userId", sp.userId(), "points", sp.points()))
                    .collect(Collectors.toList())
            )));
        } catch (SQLException e) {
            log.error("Season leaderboard error", e);
            sendError(session, "server_error");
        }
    }

    private void handleSeasonClaimRewards(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        try {
            Optional<SeasonService.Season> activeSeason = seasonService.getActive();
            if (activeSeason.isEmpty()) { sendError(session, "no_active_season"); return; }
            boolean ok = seasonService.claimRewards(activeSeason.get().id(), userId);
            if (!ok) { sendError(session, "already_claimed"); return; }
            session.send(router.buildPacket(PacketType.SEA_REWARDS_CLAIMED,
                Map.of("seasonId", activeSeason.get().id())));
        } catch (SQLException e) {
            log.error("Season claim error", e);
            sendError(session, "server_error");
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> eventToMap(EventService.Event e) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",              e.id());
        m.put("name",            e.name());
        m.put("description",     e.description());
        m.put("type",            e.type());
        m.put("startsAt",        e.startsAt());
        m.put("endsAt",          e.endsAt());
        m.put("roomId",          e.roomId());
        m.put("hostUserId",      e.hostUserId());
        m.put("maxParticipants", e.maxParticipants());
        m.put("status",          e.status());
        return m;
    }

    private Map<String, Object> compToMap(CompetitionService.Competition c) {
        return Map.of("id", c.id(), "name", c.name(), "description", c.description(),
            "gameType", c.gameType(), "format", c.format(), "status", c.status(),
            "startsAt", c.startsAt(), "endsAt", c.endsAt());
    }

    private Map<String, Object> seasonToMap(SeasonService.Season s) {
        return Map.of("id", s.id(), "name", s.name(), "slug", s.slug(),
            "startsAt", s.startsAt(), "endsAt", s.endsAt(), "description", s.description());
    }

    private void sendError(WebSocketSession session, String reason) {
        session.send(router.buildPacket(PacketType.EVENT_ERROR, Map.of("reason", reason)));
    }
}
