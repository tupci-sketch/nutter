package com.habnut.emulator.rp;

import com.habnut.emulator.db.DatabaseManager;

import java.sql.*;
import java.util.Optional;

public final class RpCharacterService {

    public record Character(long id, long userId, String name, String surname, int age,
                            String biography, Long factionId, Long jobId, int health,
                            int cashBalance, int bankBalance, String prisonExpiry,
                            String createdAt) {}

    private final DatabaseManager db;

    public RpCharacterService(DatabaseManager db) {
        this.db = db;
    }

    public Optional<Character> findByUser(long userId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, user_id, name, surname, age, biography, faction_id, job_id," +
                 " health, cash_balance, bank_balance, prison_expiry, created_at" +
                 " FROM habnut_rp_characters WHERE user_id=?")) {
            ps.setLong(1, userId);
            return mapOne(ps);
        }
    }

    public Optional<Character> findById(long charId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, user_id, name, surname, age, biography, faction_id, job_id," +
                 " health, cash_balance, bank_balance, prison_expiry, created_at" +
                 " FROM habnut_rp_characters WHERE id=?")) {
            ps.setLong(1, charId);
            return mapOne(ps);
        }
    }

    public Character create(long userId, String name, String surname, int age,
                            String biography) throws SQLException {
        try (Connection conn = db.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO habnut_rp_characters (user_id, name, surname, age, biography)" +
                     " VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, userId);
                ps.setString(2, name);
                ps.setString(3, surname);
                ps.setInt(4, age);
                ps.setString(5, biography);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    long id = keys.getLong(1);
                    return findById(id).orElseThrow();
                }
            }
        }
    }

    public boolean updateBiography(long charId, long userId, String biography) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_rp_characters SET biography=? WHERE id=? AND user_id=?")) {
            ps.setString(1, biography);
            ps.setLong(2, charId);
            ps.setLong(3, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateHealth(long charId, int delta) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_rp_characters SET health=GREATEST(0,LEAST(100,health+?)) WHERE id=?")) {
            ps.setInt(1, delta);
            ps.setLong(2, charId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean adjustCash(Connection conn, long charId, int delta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_rp_characters SET cash_balance=GREATEST(0,cash_balance+?) WHERE id=?" +
                 (delta < 0 ? " AND cash_balance >= ?" : ""))) {
            ps.setInt(1, delta);
            ps.setLong(2, charId);
            if (delta < 0) ps.setInt(3, -delta);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean adjustBank(Connection conn, long charId, int delta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_rp_characters SET bank_balance=GREATEST(0,bank_balance+?) WHERE id=?" +
                 (delta < 0 ? " AND bank_balance >= ?" : ""))) {
            ps.setInt(1, delta);
            ps.setLong(2, charId);
            if (delta < 0) ps.setInt(3, -delta);
            return ps.executeUpdate() > 0;
        }
    }

    public void setPrisonExpiry(Connection conn, long charId, String expiry) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_rp_characters SET prison_expiry=? WHERE id=?")) {
            if (expiry == null) ps.setNull(1, Types.TIMESTAMP);
            else ps.setString(1, expiry);
            ps.setLong(2, charId);
            ps.executeUpdate();
        }
    }

    public void clearJob(Connection conn, long charId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_rp_characters SET job_id=NULL WHERE id=?")) {
            ps.setLong(1, charId);
            ps.executeUpdate();
        }
    }

    public void setJob(Connection conn, long charId, long jobId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_rp_characters SET job_id=? WHERE id=?")) {
            ps.setLong(1, jobId);
            ps.setLong(2, charId);
            ps.executeUpdate();
        }
    }

    public void setFaction(Connection conn, long charId, Long factionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_rp_characters SET faction_id=? WHERE id=?")) {
            if (factionId == null) ps.setNull(1, Types.INTEGER);
            else ps.setLong(1, factionId);
            ps.setLong(2, charId);
            ps.executeUpdate();
        }
    }

    private Optional<Character> mapOne(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return Optional.empty();
            return Optional.of(fromRow(rs));
        }
    }

    private Character fromRow(ResultSet rs) throws SQLException {
        Object fid = rs.getObject("faction_id");
        Object jid = rs.getObject("job_id");
        return new Character(
            rs.getLong("id"), rs.getLong("user_id"),
            rs.getString("name"), rs.getString("surname"),
            rs.getInt("age"), rs.getString("biography"),
            fid != null ? ((Number) fid).longValue() : null,
            jid != null ? ((Number) jid).longValue() : null,
            rs.getInt("health"), rs.getInt("cash_balance"),
            rs.getInt("bank_balance"), rs.getString("prison_expiry"),
            rs.getString("created_at"));
    }

    public Connection getConnection() throws SQLException {
        return db.getConnection();
    }
}
