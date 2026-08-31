package com.habnut.emulator.rp;

import com.habnut.emulator.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class RpCrimeService {

    public record Crime(long id, long charId, String type, String description,
                        Long arrestedByCharId, String sentence, String recordedAt,
                        String expungedAt) {}
    public record Arrest(long id, long suspectCharId, long officerCharId,
                         String crimeDescription, String arrestedAt) {}

    private final DatabaseManager db;

    public RpCrimeService(DatabaseManager db) {
        this.db = db;
    }

    public long recordCrime(long charId, String type, String description,
                             Long officerCharId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_rp_crimes (character_id, type, description," +
                 " arrested_by_character_id) VALUES (?,?,?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, charId);
            ps.setString(2, type);
            ps.setString(3, description);
            if (officerCharId != null) ps.setLong(4, officerCharId);
            else ps.setNull(4, Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public Arrest arrest(long suspectCharId, long officerCharId,
                          String crimeDescription) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_rp_arrests (suspect_character_id, officer_character_id," +
                 " crime_description) VALUES (?,?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, suspectCharId);
            ps.setLong(2, officerCharId);
            ps.setString(3, crimeDescription);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                return new Arrest(id, suspectCharId, officerCharId, crimeDescription, "NOW");
            }
        }
    }

    public List<Crime> getCriminalRecord(long charId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, character_id, type, description, arrested_by_character_id," +
                 " sentence, recorded_at, expunged_at FROM habnut_rp_crimes" +
                 " WHERE character_id=? AND expunged_at IS NULL ORDER BY recorded_at DESC")) {
            ps.setLong(1, charId);
            List<Crime> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Object abId = rs.getObject("arrested_by_character_id");
                    list.add(new Crime(rs.getLong("id"), rs.getLong("character_id"),
                        rs.getString("type"), rs.getString("description"),
                        abId != null ? ((Number) abId).longValue() : null,
                        rs.getString("sentence"), rs.getString("recorded_at"),
                        rs.getString("expunged_at")));
                }
            }
            return list;
        }
    }

    public boolean expunge(long crimeId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_rp_crimes SET expunged_at=NOW() WHERE id=? AND expunged_at IS NULL")) {
            ps.setLong(1, crimeId);
            return ps.executeUpdate() > 0;
        }
    }
}
