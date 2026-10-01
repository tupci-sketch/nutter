package com.habnut.emulator.sound;

import com.fasterxml.jackson.databind.JsonNode;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.SessionRegistry;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.protocol.PacketType;
import com.habnut.emulator.room.Room;
import com.habnut.emulator.room.RoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public final class SoundHandler {

    private static final Logger log = LoggerFactory.getLogger(SoundHandler.class);

    private final SoundService    soundService;
    private final RoomManager     roomManager;
    private final SessionRegistry sessions;
    private final PacketRouter    router;

    public SoundHandler(SoundService soundService, RoomManager roomManager,
                        SessionRegistry sessions, PacketRouter router) {
        this.soundService = soundService;
        this.roomManager  = roomManager;
        this.sessions     = sessions;
        this.router       = router;
    }

    public void register(PacketRouter router) {
        router.register(PacketType.SND_PLAYLIST_GET,  this::handleGet);
        router.register(PacketType.SND_TRACK_ADD,     this::handleAdd);
        router.register(PacketType.SND_TRACK_REMOVE,  this::handleRemove);
        router.register(PacketType.SND_TRACK_REORDER, this::handleReorder);
    }

    private void handleGet(WebSocketSession session, JsonNode p) {
        long roomId = p.path("roomId").asLong(-1);
        try {
            SoundService.Playlist playlist = soundService.getPlaylist(roomId);
            session.send(router.buildPacket(PacketType.SND_PLAYLIST_RESULT, Map.of(
                "roomId", roomId,
                "currentTrack", playlist.currentTrackIdx(),
                "tracks", playlist.tracks().stream().map(this::buildTrackPayload)
                    .collect(Collectors.toList())
            )));
        } catch (SQLException e) {
            log.error("Sound get error", e);
            sendError(session, "server_error");
        }
    }

    private void handleAdd(WebSocketSession session, JsonNode p) {
        long roomId  = p.path("roomId").asLong(-1);
        long trackId = p.path("trackId").asLong(-1);
        long userId  = session.getUserId();

        Optional<Room> roomOpt = roomManager.get(roomId);
        if (roomOpt.isEmpty() || !roomOpt.get().hasRight(userId)) {
            sendError(session, "permission_denied"); return;
        }

        try {
            boolean ok = soundService.addTrack(roomId, trackId, userId);
            if (!ok) { sendError(session, "track_limit_or_not_found"); return; }
            broadcastPlaylistUpdate(roomId, roomOpt.get());
        } catch (SQLException e) {
            log.error("Sound add error", e);
            sendError(session, "server_error");
        }
    }

    private void handleRemove(WebSocketSession session, JsonNode p) {
        long roomId  = p.path("roomId").asLong(-1);
        long trackId = p.path("trackId").asLong(-1);
        long userId  = session.getUserId();

        Optional<Room> roomOpt = roomManager.get(roomId);
        if (roomOpt.isEmpty() || !roomOpt.get().hasRight(userId)) {
            sendError(session, "permission_denied"); return;
        }

        try {
            soundService.removeTrack(roomId, trackId);
            broadcastPlaylistUpdate(roomId, roomOpt.get());
        } catch (SQLException e) {
            log.error("Sound remove error", e);
            sendError(session, "server_error");
        }
    }

    private void handleReorder(WebSocketSession session, JsonNode p) {
        long roomId  = p.path("roomId").asLong(-1);
        long trackId = p.path("trackId").asLong(-1);
        int  newOrder = p.path("order").asInt(0);
        long userId   = session.getUserId();

        Optional<Room> roomOpt = roomManager.get(roomId);
        if (roomOpt.isEmpty() || !roomOpt.get().hasRight(userId)) {
            sendError(session, "permission_denied"); return;
        }

        try {
            soundService.reorderTrack(roomId, trackId, newOrder);
            broadcastPlaylistUpdate(roomId, roomOpt.get());
        } catch (SQLException e) {
            log.error("Sound reorder error", e);
            sendError(session, "server_error");
        }
    }

    private void broadcastPlaylistUpdate(long roomId, Room room) throws SQLException {
        SoundService.Playlist playlist = soundService.getPlaylist(roomId);
        String json = router.buildPacket(PacketType.SND_PLAYLIST_RESULT, Map.of(
            "roomId", roomId,
            "currentTrack", playlist.currentTrackIdx(),
            "tracks", playlist.tracks().stream().map(this::buildTrackPayload)
                .collect(Collectors.toList())
        ));
        for (long uid : room.getAllUserIds())
            sessions.byUserId(uid).ifPresent(s -> s.send(json));
    }

    private Map<String, Object> buildTrackPayload(SoundService.Track track) {
        return Map.of("id", track.id(), "name", track.name(), "artist", track.artist(),
            "durationMs", track.durationMs(), "fileUrl", track.fileUrl());
    }

    private void sendError(WebSocketSession session, String reason) {
        session.send(router.buildPacket(PacketType.SOUND_ERROR, Map.of("reason", reason)));
    }
}
