package com.habnut.emulator.garden;

import com.fasterxml.jackson.databind.JsonNode;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.SessionRegistry;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.protocol.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class GardenHandler {

    private static final Logger log = LoggerFactory.getLogger(GardenHandler.class);

    private final GardenService     gardenService;
    private final SessionRegistry   sessions;
    private final PacketRouter      router;
    private ScheduledExecutorService scheduler;

    public GardenHandler(GardenService gardenService, SessionRegistry sessions,
                         PacketRouter router) {
        this.gardenService = gardenService;
        this.sessions      = sessions;
        this.router        = router;
    }

    public void register(PacketRouter router) {
        router.register(PacketType.GARDEN_STATE,   this::handleState);
        router.register(PacketType.GARDEN_PLANT,   this::handlePlant);
        router.register(PacketType.GARDEN_WATER,   this::handleWater);
        router.register(PacketType.GARDEN_HARVEST, this::handleHarvest);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "garden-tick");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tick, 60, 60, TimeUnit.SECONDS);
    }

    public void close() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    private void handleState(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        try {
            List<GardenService.Plot> plots = gardenService.getPlotsByOwner(userId);
            List<GardenService.PlantDef> plants = gardenService.listPlants();
            List<GardenService.Goal> goals = gardenService.listActiveGoals();
            session.send(router.buildPacket(PacketType.GARDEN_STATE_RESULT, Map.of(
                "plots",  plots.stream().map(this::plotToMap).collect(Collectors.toList()),
                "plants", plants.stream().map(this::plantToMap).collect(Collectors.toList()),
                "goals",  goals.stream().map(this::goalToMap).collect(Collectors.toList())
            )));
        } catch (SQLException e) {
            log.error("Garden state error", e);
            sendError(session, "server_error");
        }
    }

    private void handlePlant(WebSocketSession session, JsonNode p) {
        long userId    = session.getUserId();
        long plotId    = p.path("plotId").asLong(-1);
        String plant   = p.path("plantType").asText("");

        if (plant.isBlank()) { sendError(session, "invalid_payload"); return; }
        try {
            String err = gardenService.plant(plotId, userId, plant);
            if (err != null) { sendError(session, err); return; }
            session.send(router.buildPacket(PacketType.GARDEN_PLANTED,
                Map.of("plotId", plotId, "plantType", plant)));
        } catch (SQLException e) {
            log.error("Garden plant error", e);
            sendError(session, "server_error");
        }
    }

    private void handleWater(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        long plotId = p.path("plotId").asLong(-1);

        try {
            String err = gardenService.water(plotId, userId);
            if (err != null) { sendError(session, err); return; }
            session.send(router.buildPacket(PacketType.GARDEN_WATERED,
                Map.of("plotId", plotId)));
        } catch (SQLException e) {
            log.error("Garden water error", e);
            sendError(session, "server_error");
        }
    }

    private void handleHarvest(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        long plotId = p.path("plotId").asLong(-1);

        try {
            GardenService.HarvestResult result = gardenService.harvest(plotId, userId);
            if (!result.ok()) { sendError(session, result.reason()); return; }
            session.send(router.buildPacket(PacketType.GARDEN_HARVESTED, Map.of(
                "plotId",          plotId,
                "yield",           result.yield(),
                "nutPoints",       result.nutPointsGranted(),
                "goalContributed", result.goalContributed()
            )));
            if (result.goalContributed()) {
                broadcastGoalUpdate();
            }
        } catch (SQLException e) {
            log.error("Garden harvest error", e);
            sendError(session, "server_error");
        }
    }

    private void tick() {
        try {
            gardenService.tickGrowth();
            gardenService.tickWithering();
        } catch (Exception e) {
            log.error("Garden tick error", e);
        }
    }

    private void broadcastGoalUpdate() {
        try {
            List<GardenService.Goal> goals = gardenService.listActiveGoals();
            String json = router.buildPacket(PacketType.GARDEN_GOAL_UPDATED,
                Map.of("goals", goals.stream().map(this::goalToMap).collect(Collectors.toList())));
            sessions.all().forEach(s -> s.send(json));
        } catch (SQLException e) {
            log.error("Goal update broadcast failed", e);
        }
    }

    private Map<String, Object> plotToMap(GardenService.Plot plot) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",           plot.id());
        m.put("ownerUserId",  plot.ownerUserId());
        m.put("plantType",    plot.plantType());
        m.put("stage",        plot.stage());
        m.put("plantedAt",    plot.plantedAt());
        m.put("wateredAt",    plot.wateredAt());
        m.put("readyAt",      plot.readyAt());
        m.put("witheredAt",   plot.witheredAt());
        m.put("harvestYield", plot.harvestYield());
        return m;
    }

    private Map<String, Object> plantToMap(GardenService.PlantDef p) {
        return Map.of("id", p.id(), "type", p.type(), "name", p.name(),
            "growthMs", p.growthMs(), "wateringIntervalMs", p.wateringIntervalMs(),
            "harvestYield", p.harvestYield(), "bonusSeason",
            p.bonusSeason() != null ? p.bonusSeason() : "");
    }

    private Map<String, Object> goalToMap(GardenService.Goal g) {
        return Map.of("id", g.id(), "description", g.description(),
            "plantType", g.plantType() != null ? g.plantType() : "",
            "targetCount", g.targetCount(), "currentCount", g.currentCount(),
            "rewardDescription", g.rewardDescription(), "expiresAt", g.expiresAt(),
            "completed", g.completed());
    }

    private void sendError(WebSocketSession session, String reason) {
        session.send(router.buildPacket(PacketType.GARDEN_ERROR, Map.of("reason", reason)));
    }
}
