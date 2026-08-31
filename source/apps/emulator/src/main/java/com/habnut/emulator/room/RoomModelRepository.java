package com.habnut.emulator.room;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RoomModelRepository {

    private static final Logger log = LoggerFactory.getLogger(RoomModelRepository.class);

    private final DatabaseManager db;
    private final ConcurrentHashMap<String, RoomModel> cache = new ConcurrentHashMap<>();

    public RoomModelRepository(DatabaseManager db) {
        this.db = db;
    }

    public Optional<RoomModel> find(String modelId) {
        RoomModel cached = cache.get(modelId);
        if (cached != null) return Optional.of(cached);

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, heightmap, door_x, door_y, door_rotation, max_visitors " +
                 "FROM habnut_room_models WHERE id = ?")) {
            ps.setString(1, modelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                RoomModel model = new RoomModel(
                    rs.getString("id"),
                    rs.getString("heightmap"),
                    rs.getInt("door_x"),
                    rs.getInt("door_y"),
                    rs.getInt("door_rotation"),
                    rs.getInt("max_visitors")
                );
                cache.put(modelId, model);
                return Optional.of(model);
            }
        } catch (SQLException e) {
            log.error("Failed to load room model '{}'", modelId, e);
            return Optional.empty();
        }
    }

    public void preloadAll() {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT id, heightmap, door_x, door_y, door_rotation, max_visitors " +
                 "FROM habnut_room_models")) {
            int count = 0;
            while (rs.next()) {
                RoomModel model = new RoomModel(
                    rs.getString("id"),
                    rs.getString("heightmap"),
                    rs.getInt("door_x"),
                    rs.getInt("door_y"),
                    rs.getInt("door_rotation"),
                    rs.getInt("max_visitors")
                );
                cache.put(model.id(), model);
                count++;
            }
            log.info("Preloaded {} room models", count);
        } catch (SQLException e) {
            log.error("Failed to preload room models", e);
        }
    }
}
