package com.habnut.emulator.world;

import com.habnut.emulator.room.Room;

import java.util.Collection;
import java.util.Optional;

public interface World {

    String getId();

    String getDisplayName();

    void loadRoom(long roomId);

    void unloadRoom(long roomId);

    Optional<Room> getRoom(long roomId);

    Collection<Room> getLoadedRooms();

    int getLoadedRoomCount();
}
