package com.habnut.emulator.events;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CompetitionService {

    private static final Logger log = LoggerFactory.getLogger(CompetitionService.class);

    public record Competition(long id, String name, String description, String gameType,
                              String format, String status, String startsAt, String endsAt) {}
    public record Participant(long userId, int score, Integer rank) {}

    private final DatabaseManager db;

    public CompetitionService(DatabaseManager db) {
        this.db = db;
    }

    public long create(String name, String description, String gameType, String format,
                       String startsAt, String endsAt) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_competitions (name, description, game_type, format," +
                 " starts_at, ends_at) VALUES (?,?,?,?,?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, description != null ? description : "");
            ps.setString(3, gameType);
            ps.setString(4, format != null ? format : "bracket");
            ps.setString(5, startsAt);
            ps.setString(6, endsAt);
            ps.executeUpdate();
            try (ResultSet gk = ps.getGeneratedKeys()) { gk.next(); return gk.getLong(1); }
        }
    }

    public List<Competition> listActive() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, name, description, game_type, format, status, starts_at, ends_at" +
                 " FROM habnut_competitions WHERE status IN ('upcoming','active')" +
                 " ORDER BY starts_at ASC LIMIT 20")) {
            return mapCompetitions(ps);
        }
    }

    public Optional<Competition> findById(long compId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, name, description, game_type, format, status, starts_at, ends_at" +
                 " FROM habnut_competitions WHERE id=?")) {
            ps.setLong(1, compId);
            List<Competition> list = mapCompetitions(ps);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }
    }

    public boolean register(long compId, long userId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT IGNORE INTO habnut_competition_participants" +
                 " (competition_id, user_id) VALUES (?,?)")) {
            ps.setLong(1, compId); ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean submitScore(long compId, long userId, int score) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_competition_participants SET score=?" +
                 " WHERE competition_id=? AND user_id=?")) {
            ps.setInt(1, score); ps.setLong(2, compId); ps.setLong(3, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean finalise(long compId) throws SQLException {
        try (Connection conn = db.getConnection()) {
            List<Participant> participants = getParticipants(conn, compId);
            participants.sort((a, b) -> Integer.compare(b.score(), a.score()));
            for (int i = 0; i < participants.size(); i++) {
                try (PreparedStatement ps = conn.prepareStatement(
                         "UPDATE habnut_competition_participants SET rank=?" +
                         " WHERE competition_id=? AND user_id=?")) {
                    ps.setInt(1, i + 1);
                    ps.setLong(2, compId);
                    ps.setLong(3, participants.get(i).userId());
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                     "UPDATE habnut_competitions SET status='finished' WHERE id=?")) {
                ps.setLong(1, compId);
                return ps.executeUpdate() > 0;
            }
        }
    }

    public List<Participant> getLeaderboard(long compId) throws SQLException {
        try (Connection conn = db.getConnection()) {
            List<Participant> list = getParticipants(conn, compId);
            list.sort((a, b) -> Integer.compare(b.score(), a.score()));
            return list;
        }
    }

    private List<Participant> getParticipants(Connection conn, long compId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT user_id, score, rank FROM habnut_competition_participants" +
                 " WHERE competition_id=? ORDER BY score DESC")) {
            ps.setLong(1, compId);
            List<Participant> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Object rankVal = rs.getObject("rank");
                    list.add(new Participant(rs.getLong("user_id"), rs.getInt("score"),
                        rankVal != null ? ((Number) rankVal).intValue() : null));
                }
            }
            return list;
        }
    }

    private List<Competition> mapCompetitions(PreparedStatement ps) throws SQLException {
        List<Competition> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Competition(rs.getLong("id"), rs.getString("name"),
                    rs.getString("description"), rs.getString("game_type"),
                    rs.getString("format"), rs.getString("status"),
                    rs.getString("starts_at"), rs.getString("ends_at")));
            }
        }
        return list;
    }
}
