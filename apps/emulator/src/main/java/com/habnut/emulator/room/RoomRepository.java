package com.habnut.emulator.room;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RoomRepository {

    private static final Logger log = LoggerFactory.getLogger(RoomRepository.class);

    private final DatabaseManager db;

    public RoomRepository(DatabaseManager db) {
        this.db = db;
    }

    private static final String SELECT =
        "SELECT r.id, r.owner_id, u.username AS owner_name, r.name, r.description, " +
        "r.model_id, r.access_type, r.password_hash, r.max_visitors, " +
        "r.allow_pets, r.allow_pets_eat, r.allow_walkthrough, r.hide_walls, " +
        "r.wall_height, r.floor_thickness, r.wall_thickness, " +
        "r.background_colour, r.landscape_colour, r.score, r.is_promoted, r.category " +
        "FROM habnut_rooms r JOIN habnut_users u ON u.id = r.owner_id ";

    public Optional<RoomSettings> findById(long id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT + "WHERE r.id = ?")) {
            ps.setLong(1, id);
            return queryFirst(ps);
        } catch (SQLException e) {
            log.error("findById failed for room {}", id, e);
            return Optional.empty();
        }
    }

    public List<RoomSettings> findByOwner(long ownerId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 SELECT + "WHERE r.owner_id = ? ORDER BY r.name")) {
            ps.setLong(1, ownerId);
            return queryList(ps);
        } catch (SQLException e) {
            log.error("findByOwner failed for user {}", ownerId, e);
            return List.of();
        }
    }

    public List<RoomSettings> searchPublic(String query, int limit) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 SELECT + "WHERE r.access_type < 3 AND r.name LIKE ? " +
                 "ORDER BY r.score DESC LIMIT ?")) {
            ps.setString(1, "%" + query + "%");
            ps.setInt(2, limit);
            return queryList(ps);
        } catch (SQLException e) {
            log.error("searchPublic failed for query '{}'", query, e);
            return List.of();
        }
    }

    public List<RoomSettings> getPopular(int limit) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 SELECT + "WHERE r.access_type < 3 ORDER BY r.score DESC LIMIT ?")) {
            ps.setInt(1, limit);
            return queryList(ps);
        } catch (SQLException e) {
            log.error("getPopular failed", e);
            return List.of();
        }
    }

    public long create(long ownerId, String name, String description, String modelId,
                       int maxVisitors) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_rooms (owner_id, name, description, model_id, max_visitors) " +
                 "VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, ownerId);
            ps.setString(2, name);
            ps.setString(3, description);
            ps.setString(4, modelId);
            ps.setInt(5, maxVisitors);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("No generated key returned");
                return rs.getLong(1);
            }
        }
    }

    public void updateScore(long roomId, int delta) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_rooms SET score = GREATEST(0, score + ?) WHERE id = ?")) {
            ps.setInt(1, delta);
            ps.setLong(2, roomId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("updateScore failed for room {}", roomId, e);
        }
    }

    /**
     * Writes a room's decoration.
     *
     * Only keys present in {@code allowedFields} are written, and each is
     * mapped to its column through that map rather than taken from the payload,
     * so a crafted packet cannot name an arbitrary column.
     *
     * @return true if the row was updated
     */
    public boolean updateDecoration(long roomId, Map<String, Object> values,
                                    Map<String, String> allowedFields) {
        List<String> assignments = new ArrayList<>();
        List<Object> bindings = new ArrayList<>();

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String column = allowedFields.get(entry.getKey());
            if (column == null) continue;
            assignments.add(column + " = ?");
            bindings.add(entry.getValue());
        }
        if (assignments.isEmpty()) return false;

        String sql = "UPDATE habnut_rooms SET " + String.join(", ", assignments) + " WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (Object binding : bindings) {
                if (binding instanceof Boolean b) ps.setBoolean(i++, b);
                else ps.setString(i++, String.valueOf(binding));
            }
            ps.setLong(i, roomId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("updateDecoration failed for room {}", roomId, e);
            return false;
        }
    }

    private Optional<RoomSettings> queryFirst(PreparedStatement ps) throws SQLException {
        List<RoomSettings> list = queryList(ps);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    private List<RoomSettings> queryList(PreparedStatement ps) throws SQLException {
        List<RoomSettings> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(map(rs));
        }
        return result;
    }

    private RoomSettings map(ResultSet rs) throws SQLException {
        return new RoomSettings(
            rs.getLong("id"),
            rs.getLong("owner_id"),
            rs.getString("owner_name"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("model_id"),
            rs.getInt("access_type"),
            rs.getString("password_hash"),
            rs.getInt("max_visitors"),
            rs.getBoolean("allow_pets"),
            rs.getBoolean("allow_pets_eat"),
            rs.getBoolean("allow_walkthrough"),
            rs.getBoolean("hide_walls"),
            rs.getInt("wall_height"),
            rs.getString("floor_thickness"),
            rs.getString("wall_thickness"),
            rs.getString("background_colour"),
            rs.getString("landscape_colour"),
            rs.getInt("score"),
            rs.getBoolean("is_promoted"),
            rs.getString("category")
        );
    }
}
