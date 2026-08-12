package com.habnut.emulator.room;

import com.habnut.emulator.metrics.MetricsRegistry;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.SessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.*;

public final class RoomManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

    private final RoomRepository roomRepo;
    private final RoomModelRepository modelRepo;
    private final PacketRouter router;
    private final SessionRegistry sessions;
    private final MetricsRegistry metrics;

    private final ConcurrentHashMap<Long, Room> loaded = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public RoomManager(RoomRepository roomRepo, RoomModelRepository modelRepo,
                       PacketRouter router, SessionRegistry sessions,
                       MetricsRegistry metrics) {
        this.roomRepo  = roomRepo;
        this.modelRepo = modelRepo;
        this.router    = router;
        this.sessions  = sessions;
        this.metrics   = metrics;
        this.scheduler = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "room-tick");
            t.setDaemon(true);
            return t;
        });
    }

    public Optional<Room> get(long roomId) {
        return Optional.ofNullable(loaded.get(roomId));
    }

    public Room load(long roomId) {
        return loaded.computeIfAbsent(roomId, id -> {
            RoomSettings settings = roomRepo.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Room not found: " + id));
            RoomModel model = modelRepo.find(settings.modelId()).orElseThrow(
                () -> new IllegalArgumentException("Room model not found: " + settings.modelId()));
            Room room = new Room(settings, model, router, sessions);
            room.startTick(scheduler);
            metrics.setActiveRooms(loaded.size());
            log.info("Room {} loaded (model={})", id, settings.modelId());
            return room;
        });
    }

    public void unloadIfEmpty(long roomId) {
        Room room = loaded.get(roomId);
        if (room != null && room.isEmpty()) {
            loaded.remove(roomId);
            room.stopTick();
            metrics.setActiveRooms(loaded.size());
            log.info("Room {} unloaded (empty)", roomId);
        }
    }

    public Collection<Room> getLoaded() {
        return loaded.values();
    }

    public int getLoadedCount() {
        return loaded.size();
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        loaded.values().forEach(Room::stopTick);
        loaded.clear();
    }
}
