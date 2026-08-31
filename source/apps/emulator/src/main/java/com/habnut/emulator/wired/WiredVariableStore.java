package com.habnut.emulator.wired;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.concurrent.ConcurrentHashMap;

public final class WiredVariableStore {

    private static final Logger log = LoggerFactory.getLogger(WiredVariableStore.class);

    // In-memory caches: room scope key="room:roomId:name", user="user:userId:name", global="global::name"
    private final ConcurrentHashMap<String, WiredValue> cache = new ConcurrentHashMap<>();
    private final DatabaseManager db;

    /**
     * @param db backing store for variable persistence, or {@code null} to keep
     *           variables in memory only. The conformance suite uses the
     *           in-memory form so wired semantics can be exercised without a
     *           database; the server always supplies a real manager.
     */
    public WiredVariableStore(DatabaseManager db) {
        this.db = db;
    }

    public WiredValue get(WiredContext.Scope scope, long roomId, long userId, String name) {
        String key = cacheKey(scope, roomId, userId, name);
        return cache.computeIfAbsent(key, k -> loadFromDb(scope, roomId, userId, name));
    }

    public void set(WiredContext.Scope scope, long roomId, long userId, String name, WiredValue value) {
        String key = cacheKey(scope, roomId, userId, name);
        cache.put(key, value);
        persistToDb(scope, roomId, userId, name, value);
    }

    public void evictRoom(long roomId) {
        cache.keySet().removeIf(k -> k.startsWith("room:" + roomId + ":"));
    }

    private String cacheKey(WiredContext.Scope scope, long roomId, long userId, String name) {
        return switch (scope) {
            case ROOM   -> "room:" + roomId + ":" + name;
            case USER   -> "user:" + userId + ":" + name;
            case GLOBAL -> "global::" + name;
        };
    }

    private WiredValue loadFromDb(WiredContext.Scope scope, long roomId, long userId, String name) {
        if (db == null) return WiredValue.ZERO;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT var_type, num_value, text_value, bool_value FROM habnut_wired_variables " +
                 "WHERE scope = ? AND room_id = ? AND user_id = ? AND var_name = ?")) {
            ps.setString(1, scope.name().toLowerCase());
            ps.setLong(2, scope == WiredContext.Scope.ROOM ? roomId : 0);
            ps.setLong(3, scope == WiredContext.Scope.USER ? userId : 0);
            ps.setString(4, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return WiredValue.ZERO;
                return switch (rs.getString("var_type")) {
                    case "text" -> WiredValue.ofText(rs.getString("text_value"));
                    case "bool" -> WiredValue.ofBool(rs.getBoolean("bool_value"));
                    default     -> WiredValue.ofNumber(rs.getDouble("num_value"));
                };
            }
        } catch (SQLException e) {
            log.error("loadFromDb wired var failed: scope={} name={}", scope, name, e);
            return WiredValue.ZERO;
        }
    }

    private void persistToDb(WiredContext.Scope scope, long roomId, long userId,
                              String name, WiredValue value) {
        if (db == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_wired_variables " +
                 "(scope, room_id, user_id, var_name, var_type, num_value, text_value, bool_value) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                 "ON DUPLICATE KEY UPDATE var_type = VALUES(var_type), " +
                 "num_value = VALUES(num_value), text_value = VALUES(text_value), " +
                 "bool_value = VALUES(bool_value), updated_at = NOW()")) {
            ps.setString(1, scope.name().toLowerCase());
            ps.setLong(2, scope == WiredContext.Scope.ROOM ? roomId : 0);
            ps.setLong(3, scope == WiredContext.Scope.USER ? userId : 0);
            ps.setString(4, name);
            ps.setString(5, value.getType().name().toLowerCase());
            ps.setDouble(6, value.asNumber());
            ps.setString(7, value.asText());
            ps.setBoolean(8, value.asBool());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("persistToDb wired var failed: scope={} name={}", scope, name, e);
        }
    }
}
