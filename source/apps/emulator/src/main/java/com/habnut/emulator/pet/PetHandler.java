package com.habnut.emulator.pet;

import com.fasterxml.jackson.databind.JsonNode;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.protocol.PacketType;
import com.habnut.emulator.room.Room;
import com.habnut.emulator.room.RoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.habnut.emulator.net.SessionRegistry;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

public final class PetHandler {

    private static final Logger log = LoggerFactory.getLogger(PetHandler.class);

    private final PetService    petService;
    private final PetAI         petAI;
    private final RoomManager   roomManager;
    private final SessionRegistry sessions;
    private final PacketRouter  router;

    public PetHandler(PetService petService, PetAI petAI, RoomManager roomManager,
                      SessionRegistry sessions, PacketRouter router) {
        this.petService  = petService;
        this.petAI       = petAI;
        this.roomManager = roomManager;
        this.sessions    = sessions;
        this.router      = router;
    }

    public void register(PacketRouter router) {
        router.register(PacketType.PET_PLACE,   this::handlePlace);
        router.register(PacketType.PET_PICKUP,  this::handlePickup);
        router.register(PacketType.PET_COMMAND, this::handleCommand);
        router.register(PacketType.PET_FEED,    this::handleFeed);
    }

    private void handlePlace(WebSocketSession session, JsonNode p) {
        long petId  = p.path("petId").asLong(-1);
        long roomId = p.path("roomId").asLong(-1);
        int  x      = p.path("x").asInt(5);
        int  y      = p.path("y").asInt(5);
        long userId = session.getUserId();

        Optional<Room> roomOpt = roomManager.get(roomId);
        if (roomOpt.isEmpty()) { sendError(session, "room_not_found"); return; }
        Room room = roomOpt.get();

        try {
            PetService.PlaceResult result = petService.place(petId, userId, roomId, x, y);
            if (!result.ok()) { sendError(session, result.reason()); return; }

            PetService.Pet pet = result.pet();
            Map<String, Object> payload = buildPetPayload(pet);
            String json = router.buildPacket(PacketType.PET_PLACED, payload);
            for (long uid : room.getAllUserIds())
                sessions.byUserId(uid).ifPresent(s -> s.send(json));

            petAI.register(petId, userId, roomId, room);
        } catch (SQLException e) {
            log.error("Pet place error", e);
            sendError(session, "server_error");
        }
    }

    private void handlePickup(WebSocketSession session, JsonNode p) {
        long petId  = p.path("petId").asLong(-1);
        long userId = session.getUserId();

        try {
            Optional<PetService.Pet> opt = petService.getPet(petId);
            if (opt.isEmpty()) { sendError(session, "not_found"); return; }

            PetService.Pet pet = opt.get();
            Long roomId = pet.currentRoomId();
            boolean ok  = petService.pickup(petId, userId);
            if (!ok) { sendError(session, "permission_denied"); return; }

            petAI.unregister(petId);

            if (roomId != null) {
                roomManager.get(roomId).ifPresent(room -> {
                    String json = router.buildPacket(PacketType.PET_PICKED_UP,
                        Map.of("petId", petId, "ownerId", userId));
                    for (long uid : room.getAllUserIds())
                        sessions.byUserId(uid).ifPresent(s -> s.send(json));
                });
            }
        } catch (SQLException e) {
            log.error("Pet pickup error", e);
            sendError(session, "server_error");
        }
    }

    private void handleCommand(WebSocketSession session, JsonNode p) {
        long   petId   = p.path("petId").asLong(-1);
        String command = p.path("command").asText("");
        long userId    = session.getUserId();

        try {
            PetService.CommandResult result = petService.handleCommand(petId, userId, command);
            if (!result.ok()) { sendError(session, result.response()); return; }

            Optional<PetService.Pet> opt = petService.getPet(petId);
            opt.flatMap(pet -> pet.currentRoomId() != null ? roomManager.get(pet.currentRoomId()) : Optional.empty())
               .ifPresent(room -> {
                   String json = router.buildPacket(PacketType.PET_STATE_CHANGED,
                       Map.of("petId", petId, "command", command, "response", result.response()));
                   for (long uid : room.getAllUserIds())
                       sessions.byUserId(uid).ifPresent(s -> s.send(json));

                   if (result.xpGained() > 0) {
                       String lvlJson = router.buildPacket(PacketType.PET_LEVELED_UP,
                           Map.of("petId", petId, "newLevel", result.xpGained()));
                       session.send(lvlJson);
                   }
               });
        } catch (SQLException e) {
            log.error("Pet command error", e);
            sendError(session, "server_error");
        }
    }

    private void handleFeed(WebSocketSession session, JsonNode p) {
        long petId  = p.path("petId").asLong(-1);
        long userId = session.getUserId();

        try {
            Map<String, Integer> newStats = petService.feed(petId, userId);
            if (newStats.isEmpty()) { sendError(session, "pet_not_found"); return; }
            session.send(router.buildPacket(PacketType.PET_STAT_UPDATED,
                Map.of("petId", petId, "stats", newStats)));
        } catch (SQLException e) {
            log.error("Pet feed error", e);
            sendError(session, "server_error");
        }
    }

    private Map<String, Object> buildPetPayload(PetService.Pet pet) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("id",         pet.id());
        m.put("ownerId",    pet.ownerId());
        m.put("name",       pet.name());
        m.put("petType",    pet.petType());
        m.put("level",      pet.level());
        m.put("figureData", pet.figureData());
        m.put("x",          pet.posX());
        m.put("y",          pet.posY());
        return m;
    }

    private void sendError(WebSocketSession session, String reason) {
        session.send(router.buildPacket(PacketType.PET_ERROR, Map.of("reason", reason)));
    }
}
