package com.habnut.nutropolis;

import com.eu.habbo.Emulator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Small helpers for the city's own queries. Failures are logged, never thrown at a player. */
final class Db {
    private static final Logger LOGGER = LoggerFactory.getLogger(Db.class);

    private Db() {}

    private static Connection connection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    private static void bind(PreparedStatement s, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
    }

    /** Rows changed. */
    static int update(String sql, Object... args) {
        try (Connection c = connection(); PreparedStatement s = c.prepareStatement(sql)) {
            bind(s, args);
            return s.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[Nutropolis] {} failed", sql, e);
            return 0;
        }
    }

    /** The new row's id, or 0. */
    static int insert(String sql, Object... args) {
        try (Connection c = connection(); PreparedStatement s = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            bind(s, args);
            s.executeUpdate();
            try (ResultSet r = s.getGeneratedKeys()) {
                return r.next() ? r.getInt(1) : 0;
            }
        } catch (SQLException e) {
            LOGGER.error("[Nutropolis] {} failed", sql, e);
            return 0;
        }
    }

    /** First row as strings, or null when there is none. */
    static String[] row(String sql, Object... args) {
        List<String[]> rows = rows(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    static List<String[]> rows(String sql, Object... args) {
        List<String[]> out = new ArrayList<>();
        try (Connection c = connection(); PreparedStatement s = c.prepareStatement(sql)) {
            bind(s, args);
            try (ResultSet r = s.executeQuery()) {
                int n = r.getMetaData().getColumnCount();
                while (r.next()) {
                    String[] row = new String[n];
                    for (int i = 0; i < n; i++) row[i] = r.getString(i + 1);
                    out.add(row);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[Nutropolis] {} failed", sql, e);
        }
        return out;
    }

    static long number(String sql, Object... args) {
        String[] r = row(sql, args);
        if (r == null || r[0] == null) return 0;
        try {
            return Long.parseLong(r[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
