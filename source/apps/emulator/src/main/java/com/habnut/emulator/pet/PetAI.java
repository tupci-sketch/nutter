package com.habnut.emulator.pet;

import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.SessionRegistry;
import com.habnut.emulator.protocol.PacketType;
import com.habnut.emulator.room.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Runs simple autonomous roaming behaviour for placed pets.
 * Each pet registered here gets a periodic wander tick.
 */
public final class PetAI {

    private static final Logger log = LoggerFactory.getLogger(PetAI.class);
    private static final int WANDER_INTERVAL_SEC = 8;
    private static final Random RNG = new Random();

    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> activePets = new ConcurrentHashMap<>();
    private final SessionRegistry   sessionRegistry;
    private final PacketRouter      router;

    public PetAI(SessionRegistry sessionRegistry, PacketRouter router) {
        this.sessionRegistry = sessionRegistry;
        this.router          = router;
        this.scheduler = Executors.newScheduledThreadPool(1,
            r -> { Thread t = new Thread(r, "pet-ai"); t.setDaemon(true); return t; });
    }

    public void register(long petId, long ownerId, long roomId, Room room) {
        if (activePets.containsKey(petId)) return;
        ScheduledFuture<?> f = scheduler.scheduleAtFixedRate(
            () -> wander(petId, ownerId, roomId, room),
            WANDER_INTERVAL_SEC, WANDER_INTERVAL_SEC, TimeUnit.SECONDS);
        activePets.put(petId, f);
    }

    public void unregister(long petId) {
        ScheduledFuture<?> f = activePets.remove(petId);
        if (f != null) f.cancel(false);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void wander(long petId, long ownerId, long roomId, Room room) {
        try {
            int newX = 1 + RNG.nextInt(17);
            int newY = 1 + RNG.nextInt(11);

            Map<String, Object> payload = Map.of(
                "petId", petId, "roomId", roomId, "x", newX, "y", newY);
            String json = router.buildPacket(PacketType.PET_MOVED, payload);

            // Broadcast to all users in the room
            for (long uid : room.getAllUserIds()) {
                sessionRegistry.byUserId(uid).ifPresent(s -> s.send(json));
            }
        } catch (Exception e) {
            log.debug("Pet AI wander error for pet {}", petId, e);
        }
    }
}
