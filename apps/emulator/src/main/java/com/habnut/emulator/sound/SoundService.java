package com.habnut.emulator.sound;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public final class SoundService {

    private static final Logger log = LoggerFactory.getLogger(SoundService.class);
    private static final int MAX_PLAYLIST_TRACKS = 20;

    public record Track(long id, String title, String artist, int durationSeconds, String sourceKey) {}
    public record Playlist(long roomId, int currentTrackIndex, List<Track> tracks) {}

    private final DatabaseManager db;

    public SoundService(DatabaseManager db) {
        this.db = db;
    }

    public Playlist getPlaylist(long roomId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT t.id, t.title, t.artist, t.duration_seconds, t.source_key, rp.track_order " +
                 "FROM habnut_sound_tracks t " +
                 "JOIN habnut_room_playlists rp ON rp.track_id = t.id " +
                 "WHERE rp.room_id=? ORDER BY rp.track_order")) {
            ps.setLong(1, roomId);
            List<Track> tracks = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tracks.add(new Track(rs.getLong("id"), rs.getString("title"),
                        rs.getString("artist"), rs.getInt("duration_seconds"),
                        rs.getString("source_key")));
                }
            }
            int currentIdx = getCurrentTrackIndex(conn, roomId);
            return new Playlist(roomId, currentIdx, tracks);
        }
    }

    public boolean addTrack(long roomId, long trackId, long userId) throws SQLException {
        try (Connection conn = db.getConnection()) {
            // Verify track exists
            try (PreparedStatement chk = conn.prepareStatement(
                "SELECT id FROM habnut_sound_tracks WHERE id=?")) {
                chk.setLong(1, trackId);
                try (ResultSet rs = chk.executeQuery()) {
                    if (!rs.next()) return false;
                }
            }
            // Check playlist size
            int count;
            try (PreparedStatement cnt = conn.prepareStatement(
                "SELECT COUNT(*) FROM habnut_room_playlists WHERE room_id=?")) {
                cnt.setLong(1, roomId);
                try (ResultSet rs = cnt.executeQuery()) { rs.next(); count = rs.getInt(1); }
            }
            if (count >= MAX_PLAYLIST_TRACKS) return false;

            try (PreparedStatement ins = conn.prepareStatement(
                "INSERT IGNORE INTO habnut_room_playlists (room_id, track_id, track_order, added_by) " +
                "VALUES (?, ?, (SELECT IFNULL(MAX(track_order),0)+1 FROM habnut_room_playlists WHERE room_id=?), ?)")) {
                ins.setLong(1, roomId); ins.setLong(2, trackId); ins.setLong(3, roomId); ins.setLong(4, userId);
                return ins.executeUpdate() > 0;
            }
        }
    }

    public boolean removeTrack(long roomId, long trackId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM habnut_room_playlists WHERE room_id=? AND track_id=?")) {
            ps.setLong(1, roomId); ps.setLong(2, trackId);
            return ps.executeUpdate() > 0;
        }
    }

    public void reorderTrack(long roomId, long trackId, int newOrder) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_room_playlists SET track_order=? WHERE room_id=? AND track_id=?")) {
            ps.setInt(1, newOrder); ps.setLong(2, roomId); ps.setLong(3, trackId);
            ps.executeUpdate();
        }
    }

    public int advanceTrack(long roomId) throws SQLException {
        Playlist playlist = getPlaylist(roomId);
        if (playlist.tracks().isEmpty()) return -1;
        int next = (playlist.currentTrackIndex() + 1) % playlist.tracks().size();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_room_sound_state (room_id, current_track_index) VALUES (?,?) " +
                 "ON DUPLICATE KEY UPDATE current_track_index=?")) {
            ps.setLong(1, roomId); ps.setInt(2, next); ps.setInt(3, next);
            ps.executeUpdate();
        }
        return next;
    }

    private int getCurrentTrackIndex(Connection conn, long roomId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT current_track_index FROM habnut_room_sound_state WHERE room_id=?")) {
            ps.setLong(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("current_track_index") : 0;
            }
        }
    }
}
