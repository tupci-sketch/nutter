package com.habnut.emulator.economy;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    public record InventoryItem(long id, long baseId, String spriteId, String name,
                                String type, String extraData) {}

    private final DatabaseManager db;

    public InventoryService(DatabaseManager db) {
        this.db = db;
    }

    public List<InventoryItem> list(long userId, int offset, int limit) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT ii.id, ii.base_id, ib.sprite_id, ib.name, ib.type, ii.extra_data " +
                 "FROM habnut_items_inventory ii " +
                 "JOIN habnut_items_base ib ON ib.id = ii.base_id " +
                 "WHERE ii.owner_id = ? ORDER BY ii.id LIMIT ? OFFSET ?")) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            List<InventoryItem> items = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new InventoryItem(
                        rs.getLong("id"),
                        rs.getLong("base_id"),
                        rs.getString("sprite_id"),
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getString("extra_data")
                    ));
                }
            }
            return items;
        } catch (SQLException e) {
            log.error("Inventory list failed for user {}", userId, e);
            return List.of();
        }
    }

    public long grantItem(long userId, long baseId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_items_inventory (owner_id, base_id) VALUES (?, ?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setLong(2, baseId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("No key returned");
                return rs.getLong(1);
            }
        }
    }

    public int count(long userId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM habnut_items_inventory WHERE owner_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            log.error("Inventory count failed for user {}", userId, e);
            return 0;
        }
    }
}
