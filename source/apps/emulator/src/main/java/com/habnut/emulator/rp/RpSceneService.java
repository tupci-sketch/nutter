package com.habnut.emulator.rp;

import com.habnut.emulator.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RpSceneService {

    public record Scene(long id, long locationRoomId, long hostCharId, String title,
                        String description, String startedAt, String endedAt) {}

    private final DatabaseManager db;

    public RpSceneService(DatabaseManager db) {
        this.db = db;
    }

    public long start(long roomId, long hostCharId, String title,
                       String description) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_rp_scenes (location_room_id, host_character_id, title, description)" +
                 " VALUES (?,?,?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, roomId);
            ps.setLong(2, hostCharId);
            ps.setString(3, title);
            ps.setString(4, description);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public boolean end(long sceneId, long hostCharId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_rp_scenes SET ended_at=NOW()" +
                 " WHERE id=? AND host_character_id=? AND ended_at IS NULL")) {
            ps.setLong(1, sceneId);
            ps.setLong(2, hostCharId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<Scene> listActiveInRoom(long roomId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, location_room_id, host_character_id, title, description," +
                 " started_at, ended_at FROM habnut_rp_scenes" +
                 " WHERE location_room_id=? AND ended_at IS NULL ORDER BY started_at")) {
            ps.setLong(1, roomId);
            return mapScenes(ps);
        }
    }

    public Optional<Scene> findById(long sceneId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, location_room_id, host_character_id, title, description," +
                 " started_at, ended_at FROM habnut_rp_scenes WHERE id=?")) {
            ps.setLong(1, sceneId);
            List<Scene> list = mapScenes(ps);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }
    }

    private List<Scene> mapScenes(PreparedStatement ps) throws SQLException {
        List<Scene> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Scene(rs.getLong("id"), rs.getLong("location_room_id"),
                    rs.getLong("host_character_id"), rs.getString("title"),
                    rs.getString("description"), rs.getString("started_at"),
                    rs.getString("ended_at")));
            }
        }
        return list;
    }
}
