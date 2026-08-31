package com.habnut.emulator.rp;

import com.habnut.emulator.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class RpMedicalService {

    public record MedicalRecord(long id, long charId, Long treatedByCharId,
                                String conditionDesc, String treatment,
                                String admittedAt, String dischargedAt) {}

    private final DatabaseManager db;
    private final RpCharacterService charService;

    public RpMedicalService(DatabaseManager db, RpCharacterService charService) {
        this.db = db;
        this.charService = charService;
    }

    public long admit(long charId, String conditionDesc) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_rp_medical_records (character_id, condition_desc)" +
                 " VALUES (?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, charId);
            ps.setString(2, conditionDesc);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public boolean treat(long recordId, long treatingCharId, String treatment,
                          int healthRestore) throws SQLException {
        try (Connection conn = db.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                     "UPDATE habnut_rp_medical_records SET treated_by_character_id=?," +
                     " treatment=? WHERE id=? AND discharged_at IS NULL")) {
                ps.setLong(1, treatingCharId);
                ps.setString(2, treatment);
                ps.setLong(3, recordId);
                if (ps.executeUpdate() == 0) return false;
            }
            MedicalRecord mr = getRecord(conn, recordId);
            if (mr != null) charService.updateHealth(mr.charId(), healthRestore);
            return true;
        }
    }

    public boolean discharge(long recordId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_rp_medical_records SET discharged_at=NOW()" +
                 " WHERE id=? AND discharged_at IS NULL")) {
            ps.setLong(1, recordId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<MedicalRecord> getRecords(long charId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, character_id, treated_by_character_id, condition_desc," +
                 " treatment, admitted_at, discharged_at" +
                 " FROM habnut_rp_medical_records WHERE character_id=?" +
                 " ORDER BY admitted_at DESC LIMIT 20")) {
            ps.setLong(1, charId);
            return mapRecords(ps);
        }
    }

    private MedicalRecord getRecord(Connection conn, long recordId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, character_id, treated_by_character_id, condition_desc," +
                 " treatment, admitted_at, discharged_at" +
                 " FROM habnut_rp_medical_records WHERE id=?")) {
            ps.setLong(1, recordId);
            List<MedicalRecord> list = mapRecords(ps);
            return list.isEmpty() ? null : list.get(0);
        }
    }

    private List<MedicalRecord> mapRecords(PreparedStatement ps) throws SQLException {
        List<MedicalRecord> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Object tid = rs.getObject("treated_by_character_id");
                list.add(new MedicalRecord(rs.getLong("id"), rs.getLong("character_id"),
                    tid != null ? ((Number) tid).longValue() : null,
                    rs.getString("condition_desc"), rs.getString("treatment"),
                    rs.getString("admitted_at"), rs.getString("discharged_at")));
            }
        }
        return list;
    }
}
