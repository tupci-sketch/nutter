package com.habnut.emulator.rp;

import com.habnut.emulator.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RpCourtService {

    public record CourtCase(long id, long crimeId, long defendantId, Long prosecutorId,
                            Long judgeId, String verdict, String sentenceType,
                            Integer sentenceHours, Integer fineAmount,
                            String scheduledAt, String completedAt) {}
    public record PrisonRecord(long id, long charId, Long crimeId, String sentenceStart,
                               String sentenceEnd, boolean releasedEarly,
                               String releasedAt, String releaseReason) {}

    private final DatabaseManager db;
    private final RpCharacterService charService;

    public RpCourtService(DatabaseManager db, RpCharacterService charService) {
        this.db = db;
        this.charService = charService;
    }

    public long openCase(long crimeId, long defendantId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_rp_court_cases (crime_id, defendant_id) VALUES (?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, crimeId);
            ps.setLong(2, defendantId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public List<CourtCase> listPending() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, crime_id, defendant_id, prosecutor_id, judge_id, verdict," +
                 " sentence_type, sentence_hours, fine_amount, scheduled_at, completed_at" +
                 " FROM habnut_rp_court_cases WHERE verdict='pending' ORDER BY id")) {
            return mapCases(ps);
        }
    }

    public Optional<CourtCase> findCase(long caseId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, crime_id, defendant_id, prosecutor_id, judge_id, verdict," +
                 " sentence_type, sentence_hours, fine_amount, scheduled_at, completed_at" +
                 " FROM habnut_rp_court_cases WHERE id=?")) {
            ps.setLong(1, caseId);
            List<CourtCase> list = mapCases(ps);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }
    }

    public boolean renderVerdict(long caseId, long judgeCharId, String verdict,
                                  String sentenceType, Integer sentenceHours,
                                  Integer fineAmount) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_rp_court_cases SET judge_id=?, verdict=?, sentence_type=?," +
                 " sentence_hours=?, fine_amount=?, completed_at=NOW()" +
                 " WHERE id=? AND verdict='pending'")) {
            ps.setLong(1, judgeCharId);
            ps.setString(2, verdict);
            if (sentenceType != null) ps.setString(3, sentenceType); else ps.setNull(3, Types.VARCHAR);
            if (sentenceHours != null) ps.setInt(4, sentenceHours); else ps.setNull(4, Types.SMALLINT);
            if (fineAmount != null) ps.setInt(5, fineAmount); else ps.setNull(5, Types.INTEGER);
            ps.setLong(6, caseId);
            boolean ok = ps.executeUpdate() > 0;

            if (ok && "guilty".equals(verdict) && sentenceHours != null && sentenceHours > 0) {
                CourtCase cc = findCase(caseId).orElse(null);
                if (cc != null) {
                    imprison(conn, cc.defendantId(), null, sentenceHours);
                }
            }
            return ok;
        }
    }

    public long imprison(Connection conn, long charId, Long crimeId,
                          int sentenceHours) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_rp_prison (character_id, crime_id, sentence_end)" +
                 " VALUES (?, ?, DATE_ADD(NOW(), INTERVAL ? HOUR))",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, charId);
            if (crimeId != null) ps.setLong(2, crimeId); else ps.setNull(2, Types.INTEGER);
            ps.setInt(3, sentenceHours);
            ps.executeUpdate();
            charService.setPrisonExpiry(conn, charId,
                "DATE_ADD(NOW(), INTERVAL " + sentenceHours + " HOUR)");
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public boolean release(long charId, String reason) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_rp_prison SET released_early=1, released_at=NOW(), release_reason=?" +
                 " WHERE character_id=? AND released_at IS NULL")) {
            ps.setString(1, reason);
            ps.setLong(2, charId);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) charService.setPrisonExpiry(conn, charId, null);
            return ok;
        }
    }

    public Optional<PrisonRecord> getActivePrison(long charId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, character_id, crime_id, sentence_start, sentence_end," +
                 " released_early, released_at, release_reason FROM habnut_rp_prison" +
                 " WHERE character_id=? AND released_at IS NULL LIMIT 1")) {
            ps.setLong(1, charId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Object cid = rs.getObject("crime_id");
                return Optional.of(new PrisonRecord(rs.getLong("id"), rs.getLong("character_id"),
                    cid != null ? ((Number) cid).longValue() : null,
                    rs.getString("sentence_start"), rs.getString("sentence_end"),
                    rs.getBoolean("released_early"), rs.getString("released_at"),
                    rs.getString("release_reason")));
            }
        }
    }

    private List<CourtCase> mapCases(PreparedStatement ps) throws SQLException {
        List<CourtCase> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Object pid = rs.getObject("prosecutor_id");
                Object jid = rs.getObject("judge_id");
                Object sh  = rs.getObject("sentence_hours");
                Object fa  = rs.getObject("fine_amount");
                list.add(new CourtCase(rs.getLong("id"), rs.getLong("crime_id"),
                    rs.getLong("defendant_id"),
                    pid != null ? ((Number) pid).longValue() : null,
                    jid != null ? ((Number) jid).longValue() : null,
                    rs.getString("verdict"), rs.getString("sentence_type"),
                    sh != null ? ((Number) sh).intValue() : null,
                    fa != null ? ((Number) fa).intValue() : null,
                    rs.getString("scheduled_at"), rs.getString("completed_at")));
            }
        }
        return list;
    }
}
