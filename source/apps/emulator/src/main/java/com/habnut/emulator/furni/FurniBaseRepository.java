package com.habnut.emulator.furni;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class FurniBaseRepository {

    private static final Logger log = LoggerFactory.getLogger(FurniBaseRepository.class);

    private final DatabaseManager db;
    private final ConcurrentHashMap<Long, FurniBase> cache = new ConcurrentHashMap<>();

    public FurniBaseRepository(DatabaseManager db) {
        this.db = db;
    }

    public Optional<FurniBase> find(long id) {
        FurniBase cached = cache.get(id);
        if (cached != null) return Optional.of(cached);
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM habnut_items_base WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                FurniBase base = map(rs);
                cache.put(id, base);
                return Optional.of(base);
            }
        } catch (SQLException e) {
            log.error("FurniBase.find failed for id {}", id, e);
            return Optional.empty();
        }
    }

    public void preloadAll() {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM habnut_items_base")) {
            int count = 0;
            while (rs.next()) {
                FurniBase base = map(rs);
                cache.put(base.id(), base);
                count++;
            }
            log.info("Preloaded {} furni base definitions", count);
        } catch (SQLException e) {
            log.error("FurniBase preload failed", e);
        }
    }

    private FurniBase map(ResultSet rs) throws SQLException {
        return new FurniBase(
            rs.getLong("id"),
            rs.getString("sprite_id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("type"),
            rs.getInt("width"),
            rs.getInt("length"),
            rs.getDouble("stack_height"),
            rs.getBoolean("can_sit"),
            // the column is is_walkable; can_walk never existed
            rs.getBoolean("is_walkable"),
            rs.getBoolean("can_stack"),
            rs.getInt("interaction_modes"),
            rs.getString("interaction_type")
        );
    }
}
