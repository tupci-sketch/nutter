package com.habnut.emulator.rp;

import com.habnut.emulator.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RpGovernmentService {

    public record GovtOffice(long id, String title, Long holderCharId,
                              String electedAt, String termEndsAt) {}
    public record Election(long id, long officeId, String status,
                           String nominationOpenAt, String votingOpenAt,
                           String votingCloseAt) {}
    public record Candidate(long electionId, long charId, String platform,
                            int voteCount, String nominatedAt) {}
    public record Decree(long id, long issuedByCharId, String title, String body,
                         String issuedAt, String expiresAt) {}
    public record Law(long id, String title, String body, long enactedByCharId,
                      String enactedAt, String repealedAt) {}

    private final DatabaseManager db;

    public RpGovernmentService(DatabaseManager db) {
        this.db = db;
    }

    public List<GovtOffice> listOffices() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, title, holder_character_id, elected_at, term_ends_at" +
                 " FROM habnut_rp_government_offices ORDER BY id")) {
            return mapOffices(ps);
        }
    }

    public List<Election> listElections(String status) throws SQLException {
        String sql = "SELECT id, office_id, status, nomination_open_at, voting_open_at," +
                     " voting_close_at FROM habnut_rp_elections" +
                     (status != null ? " WHERE status=?" : "") + " ORDER BY voting_close_at DESC";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (status != null) ps.setString(1, status);
            List<Election> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Election(rs.getLong("id"), rs.getLong("office_id"),
                        rs.getString("status"), rs.getString("nomination_open_at"),
                        rs.getString("voting_open_at"), rs.getString("voting_close_at")));
                }
            }
            return list;
        }
    }

    public boolean nominate(long electionId, long charId, String platform) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT IGNORE INTO habnut_rp_election_candidates" +
                 " (election_id, character_id, platform) VALUES (?,?,?)")) {
            ps.setLong(1, electionId);
            ps.setLong(2, charId);
            ps.setString(3, platform);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean vote(long electionId, long voterCharId, long candidateCharId) throws SQLException {
        try (Connection conn = db.getConnection()) {
            try (PreparedStatement chk = conn.prepareStatement(
                     "SELECT status FROM habnut_rp_elections WHERE id=?")) {
                chk.setLong(1, electionId);
                try (ResultSet rs = chk.executeQuery()) {
                    if (!rs.next() || !"voting".equals(rs.getString("status"))) return false;
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                     "INSERT IGNORE INTO habnut_rp_election_votes" +
                     " (election_id, voter_character_id, candidate_character_id) VALUES (?,?,?)")) {
                ps.setLong(1, electionId);
                ps.setLong(2, voterCharId);
                ps.setLong(3, candidateCharId);
                boolean ok = ps.executeUpdate() > 0;
                if (ok) {
                    try (PreparedStatement upd = conn.prepareStatement(
                             "UPDATE habnut_rp_election_candidates SET vote_count=vote_count+1" +
                             " WHERE election_id=? AND character_id=?")) {
                        upd.setLong(1, electionId);
                        upd.setLong(2, candidateCharId);
                        upd.executeUpdate();
                    }
                }
                return ok;
            }
        }
    }

    public List<Candidate> getCandidates(long electionId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT election_id, character_id, platform, vote_count, nominated_at" +
                 " FROM habnut_rp_election_candidates WHERE election_id=?" +
                 " ORDER BY vote_count DESC")) {
            ps.setLong(1, electionId);
            List<Candidate> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Candidate(rs.getLong("election_id"),
                        rs.getLong("character_id"), rs.getString("platform"),
                        rs.getInt("vote_count"), rs.getString("nominated_at")));
                }
            }
            return list;
        }
    }

    public long issueDecree(long charId, String title, String body,
                             String expiresAt) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_rp_government_decrees" +
                 " (issued_by_character_id, title, body, expires_at) VALUES (?,?,?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, charId);
            ps.setString(2, title);
            ps.setString(3, body);
            if (expiresAt != null && !expiresAt.isBlank()) ps.setString(4, expiresAt);
            else ps.setNull(4, Types.TIMESTAMP);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public List<Law> listActiveLaws() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, title, body, enacted_by_character_id, enacted_at, repealed_at" +
                 " FROM habnut_rp_laws WHERE repealed_at IS NULL ORDER BY enacted_at DESC")) {
            List<Law> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Law(rs.getLong("id"), rs.getString("title"),
                        rs.getString("body"), rs.getLong("enacted_by_character_id"),
                        rs.getString("enacted_at"), rs.getString("repealed_at")));
                }
            }
            return list;
        }
    }

    private List<GovtOffice> mapOffices(PreparedStatement ps) throws SQLException {
        List<GovtOffice> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Object hid = rs.getObject("holder_character_id");
                list.add(new GovtOffice(rs.getLong("id"), rs.getString("title"),
                    hid != null ? ((Number) hid).longValue() : null,
                    rs.getString("elected_at"), rs.getString("term_ends_at")));
            }
        }
        return list;
    }
}
