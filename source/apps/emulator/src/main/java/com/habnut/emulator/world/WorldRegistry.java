package com.habnut.emulator.world;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldRegistry {

    private final Map<String, World> worlds = new ConcurrentHashMap<>();

    public void register(World world) {
        worlds.put(world.getId(), world);
    }

    public Optional<World> get(String worldId) {
        return Optional.ofNullable(worlds.get(worldId));
    }

    public Collection<World> all() {
        return worlds.values();
    }
}
