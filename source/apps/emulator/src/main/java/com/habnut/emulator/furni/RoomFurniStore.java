package com.habnut.emulator.furni;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RoomFurniStore {

    private static final Logger log = LoggerFactory.getLogger(RoomFurniStore.class);

    private final DatabaseManager db;
    private final FurniBaseRepository bases;
    private final long roomId;

    private final ConcurrentHashMap<Long, FloorItem> floorItems = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, WallItem>  wallItems  = new ConcurrentHashMap<>();

    public RoomFurniStore(DatabaseManager db, FurniBaseRepository bases, long roomId) {
        this.db     = db;
        this.bases  = bases;
        this.roomId = roomId;
    }

    public void load() {
        loadFloor();
        loadWall();
    }

    private void loadFloor() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT fi.id, fi.owner_id, fi.base_id, fi.x, fi.y, fi.z, fi.rotation, fi.extra_data " +
                 "FROM habnut_floor_items fi WHERE fi.room_id = ?")) {
            ps.setLong(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long baseId = rs.getLong("base_id");
                    bases.find(baseId).ifPresent(base -> {
                        try {
                            FloorItem item = new FloorItem(
                                rs.getLong("id"), rs.getLong("owner_id"), base,
                                rs.getInt("x"), rs.getInt("y"), rs.getDouble("z"),
                                rs.getInt("rotation"), rs.getString("extra_data"));
                            floorItems.put(item.id, item);
                        } catch (SQLException e) {
                            log.warn("Floor item mapping error", e);
                        }
                    });
                }
            }
        } catch (SQLException e) {
            log.error("Failed to load floor items for room {}", roomId, e);
        }
    }

    private void loadWall() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, owner_id, base_id, wall_position, extra_data " +
                 "FROM habnut_wall_items WHERE room_id = ?")) {
            ps.setLong(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long baseId = rs.getLong("base_id");
                    bases.find(baseId).ifPresent(base -> {
                        try {
                            WallItem item = new WallItem(
                                rs.getLong("id"), rs.getLong("owner_id"), base,
                                rs.getString("wall_position"), rs.getString("extra_data"));
                            wallItems.put(item.id, item);
                        } catch (SQLException e) {
                            log.warn("Wall item mapping error", e);
                        }
                    });
                }
            }
        } catch (SQLException e) {
            log.error("Failed to load wall items for room {}", roomId, e);
        }
    }

    public long placeFloor(long inventoryItemId, long ownerId, long baseId,
                           int x, int y, double z, int rotation) throws SQLException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            long newId;
            try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO habnut_floor_items (room_id, owner_id, base_id, x, y, z, rotation) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ins.setLong(1, roomId); ins.setLong(2, ownerId); ins.setLong(3, baseId);
                ins.setInt(4, x); ins.setInt(5, y); ins.setDouble(6, z); ins.setInt(7, rotation);
                ins.executeUpdate();
                try (ResultSet rs = ins.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("No key");
                    newId = rs.getLong(1);
                }
            }
            try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM habnut_items_inventory WHERE id = ? AND owner_id = ?")) {
                del.setLong(1, inventoryItemId); del.setLong(2, ownerId);
                del.executeUpdate();
            }
            conn.commit();
            bases.find(baseId).ifPresent(base ->
                floorItems.put(newId, new FloorItem(newId, ownerId, base, x, y, z, rotation, "")));
            return newId;
        }
    }

    public long pickupFloor(long itemId, long ownerId) throws SQLException {
        FloorItem item = floorItems.get(itemId);
        if (item == null) throw new IllegalArgumentException("Item not in room");
        if (item.ownerId != ownerId) throw new SecurityException("Not owner");
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            long newInvId;
            try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO habnut_items_inventory (owner_id, base_id) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
                ins.setLong(1, ownerId); ins.setLong(2, item.base.id());
                ins.executeUpdate();
                try (ResultSet rs = ins.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("No key");
                    newInvId = rs.getLong(1);
                }
            }
            try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM habnut_floor_items WHERE id = ?")) {
                del.setLong(1, itemId);
                del.executeUpdate();
            }
            conn.commit();
            floorItems.remove(itemId);
            return newInvId;
        }
    }

    public void moveFloor(long itemId, int x, int y, double z, int rotation) throws SQLException {
        FloorItem item = floorItems.get(itemId);
        if (item == null) throw new IllegalArgumentException("Item not in room");
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_floor_items SET x=?, y=?, z=?, rotation=? WHERE id=?")) {
            ps.setInt(1, x); ps.setInt(2, y); ps.setDouble(3, z);
            ps.setInt(4, rotation); ps.setLong(5, itemId);
            ps.executeUpdate();
        }
        item.setPosition(x, y, z, rotation);
    }

    public void placeWall(long inventoryItemId, long ownerId, long baseId,
                          String wallPos) throws SQLException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            long newId;
            try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO habnut_wall_items (room_id, owner_id, base_id, wall_position) " +
                "VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ins.setLong(1, roomId); ins.setLong(2, ownerId);
                ins.setLong(3, baseId); ins.setString(4, wallPos);
                ins.executeUpdate();
                try (ResultSet rs = ins.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("No key");
                    newId = rs.getLong(1);
                }
            }
            try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM habnut_items_inventory WHERE id = ? AND owner_id = ?")) {
                del.setLong(1, inventoryItemId); del.setLong(2, ownerId);
                del.executeUpdate();
            }
            conn.commit();
            bases.find(baseId).ifPresent(base ->
                wallItems.put(newId, new WallItem(newId, ownerId, base, wallPos, "")));
        }
    }

    public Optional<FloorItem> getFloor(long id)  { return Optional.ofNullable(floorItems.get(id)); }
    public Optional<WallItem>  getWall(long id)   { return Optional.ofNullable(wallItems.get(id)); }
    public Collection<FloorItem> allFloor()        { return floorItems.values(); }
    public Collection<WallItem>  allWall()         { return wallItems.values(); }
}
