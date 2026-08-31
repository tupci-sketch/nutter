package com.habnut.emulator.game;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public final class TournamentService {

    private static final Logger log = LoggerFactory.getLogger(TournamentService.class);

    private final DatabaseManager db;

    public TournamentService(DatabaseManager db) {
        this.db = db;
    }

    // --- Records ---

    public record Tournament(
        long id, String name, String gameType, String status,
        int maxTeams, int currentTeams, String startAt, String createdAt
    ) {}

    public record TournamentTeam(long id, long tournamentId, String teamName, long captainId) {}

    public record BracketMatch(
        long id, long tournamentId, int round, int matchNumber,
        Long teamAId, Long teamBId, Long winnerId, String status
    ) {}

    public enum TournamentStatus { OPEN, IN_PROGRESS, COMPLETED, CANCELLED }
    public enum RegisterResult   { REGISTERED, ALREADY_REGISTERED, TOURNAMENT_FULL, NOT_FOUND, CLOSED }

    // --- Queries ---

    public List<Tournament> listOpen() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, name, game_type, status, max_teams, current_teams, start_at, created_at " +
                 "FROM habnut_tournaments WHERE status='OPEN' ORDER BY start_at ASC LIMIT 50")) {
            return collectTournaments(ps);
        }
    }

    public Optional<Tournament> findById(long id) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, name, game_type, status, max_teams, current_teams, start_at, created_at " +
                 "FROM habnut_tournaments WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapTournament(rs)) : Optional.empty();
            }
        }
    }

    public RegisterResult register(long tournamentId, long userId, String teamName) throws SQLException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Lock tournament row
                try (PreparedStatement lock = conn.prepareStatement(
                    "SELECT status, max_teams, current_teams FROM habnut_tournaments WHERE id=? FOR UPDATE")) {
                    lock.setLong(1, tournamentId);
                    try (ResultSet rs = lock.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return RegisterResult.NOT_FOUND; }
                        String status   = rs.getString("status");
                        int    maxTeams = rs.getInt("max_teams");
                        int    current  = rs.getInt("current_teams");
                        if (!"OPEN".equals(status)) { conn.rollback(); return RegisterResult.CLOSED; }
                        if (current >= maxTeams)    { conn.rollback(); return RegisterResult.TOURNAMENT_FULL; }
                    }
                }

                // Check not already registered
                try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT id FROM habnut_tournament_teams WHERE tournament_id=? AND captain_id=?")) {
                    chk.setLong(1, tournamentId); chk.setLong(2, userId);
                    try (ResultSet rs = chk.executeQuery()) {
                        if (rs.next()) { conn.rollback(); return RegisterResult.ALREADY_REGISTERED; }
                    }
                }

                // Insert team
                try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO habnut_tournament_teams (tournament_id, team_name, captain_id, created_at) " +
                    "VALUES (?,?,?,NOW())")) {
                    ins.setLong(1, tournamentId); ins.setString(2, teamName); ins.setLong(3, userId);
                    ins.executeUpdate();
                }

                // Increment counter
                try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE habnut_tournaments SET current_teams = current_teams + 1 WHERE id=?")) {
                    upd.setLong(1, tournamentId);
                    upd.executeUpdate();
                }

                conn.commit();
                return RegisterResult.REGISTERED;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<BracketMatch> getBracket(long tournamentId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, tournament_id, round, match_number, team_a_id, team_b_id, winner_id, status " +
                 "FROM habnut_tournament_bracket WHERE tournament_id=? ORDER BY round, match_number")) {
            ps.setLong(1, tournamentId);
            List<BracketMatch> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new BracketMatch(
                        rs.getLong("id"), rs.getLong("tournament_id"),
                        rs.getInt("round"), rs.getInt("match_number"),
                        rs.getObject("team_a_id", Long.class),
                        rs.getObject("team_b_id", Long.class),
                        rs.getObject("winner_id", Long.class),
                        rs.getString("status")));
                }
            }
            return result;
        }
    }

    public void generateBracket(long tournamentId) throws SQLException {
        List<TournamentTeam> teams = getTeams(tournamentId);
        Collections.shuffle(teams);

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Delete any existing bracket
                try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM habnut_tournament_bracket WHERE tournament_id=?")) {
                    del.setLong(1, tournamentId); del.executeUpdate();
                }

                int round = 1, matchNum = 1;
                for (int i = 0; i + 1 < teams.size(); i += 2) {
                    try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO habnut_tournament_bracket " +
                        "(tournament_id, round, match_number, team_a_id, team_b_id, status) " +
                        "VALUES (?,?,?,?,?,'PENDING')")) {
                        ins.setLong(1, tournamentId); ins.setInt(2, round); ins.setInt(3, matchNum++);
                        ins.setLong(4, teams.get(i).id()); ins.setLong(5, teams.get(i + 1).id());
                        ins.executeUpdate();
                    }
                }

                try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE habnut_tournaments SET status='IN_PROGRESS' WHERE id=?")) {
                    upd.setLong(1, tournamentId); upd.executeUpdate();
                }

                conn.commit();
                log.info("Generated bracket for tournament {} with {} teams", tournamentId, teams.size());
            } catch (SQLException e) {
                conn.rollback(); throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public void recordBracketResult(long bracketMatchId, long winnerTeamId) throws SQLException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE habnut_tournament_bracket SET winner_id=?, status='COMPLETED' WHERE id=?")) {
                    upd.setLong(1, winnerTeamId); upd.setLong(2, bracketMatchId);
                    upd.executeUpdate();
                }

                // Advance winner to next round
                try (PreparedStatement sel = conn.prepareStatement(
                    "SELECT tournament_id, round, match_number FROM habnut_tournament_bracket WHERE id=?")) {
                    sel.setLong(1, bracketMatchId);
                    try (ResultSet rs = sel.executeQuery()) {
                        if (rs.next()) {
                            long tId   = rs.getLong("tournament_id");
                            int  round = rs.getInt("round");
                            int  mNum  = rs.getInt("match_number");
                            advanceWinner(conn, tId, round, mNum, winnerTeamId);
                        }
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback(); throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void advanceWinner(Connection conn, long tournamentId, int completedRound,
                                int completedMatch, long winnerTeamId) throws SQLException {
        // Count total matches in this round
        int totalInRound;
        try (PreparedStatement cnt = conn.prepareStatement(
            "SELECT COUNT(*) FROM habnut_tournament_bracket WHERE tournament_id=? AND round=?")) {
            cnt.setLong(1, tournamentId); cnt.setInt(2, completedRound);
            try (ResultSet rs = cnt.executeQuery()) { rs.next(); totalInRound = rs.getInt(1); }
        }

        // Check if all matches in this round are done
        int doneInRound;
        try (PreparedStatement cnt = conn.prepareStatement(
            "SELECT COUNT(*) FROM habnut_tournament_bracket WHERE tournament_id=? AND round=? AND status='COMPLETED'")) {
            cnt.setLong(1, tournamentId); cnt.setInt(2, completedRound);
            try (ResultSet rs = cnt.executeQuery()) { rs.next(); doneInRound = rs.getInt(1); }
        }

        if (doneInRound < totalInRound) return; // round not complete

        if (totalInRound == 1) {
            // Final match done → tournament complete
            try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE habnut_tournaments SET status='COMPLETED', winner_team_id=? WHERE id=?")) {
                upd.setLong(1, winnerTeamId); upd.setLong(2, tournamentId);
                upd.executeUpdate();
            }
            log.info("Tournament {} completed, winner team {}", tournamentId, winnerTeamId);
            return;
        }

        // Collect winners from completed round and seed next round
        List<Long> winners = new ArrayList<>();
        try (PreparedStatement sel = conn.prepareStatement(
            "SELECT winner_id FROM habnut_tournament_bracket WHERE tournament_id=? AND round=? ORDER BY match_number")) {
            sel.setLong(1, tournamentId); sel.setInt(2, completedRound);
            try (ResultSet rs = sel.executeQuery()) {
                while (rs.next()) winners.add(rs.getLong("winner_id"));
            }
        }

        int nextRound = completedRound + 1, matchNum = 1;
        for (int i = 0; i + 1 < winners.size(); i += 2) {
            try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO habnut_tournament_bracket " +
                "(tournament_id, round, match_number, team_a_id, team_b_id, status) " +
                "VALUES (?,?,?,?,?,'PENDING')")) {
                ins.setLong(1, tournamentId); ins.setInt(2, nextRound); ins.setInt(3, matchNum++);
                ins.setLong(4, winners.get(i)); ins.setLong(5, winners.get(i + 1));
                ins.executeUpdate();
            }
        }
    }

    private List<TournamentTeam> getTeams(long tournamentId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, tournament_id, team_name, captain_id FROM habnut_tournament_teams WHERE tournament_id=?")) {
            ps.setLong(1, tournamentId);
            List<TournamentTeam> teams = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    teams.add(new TournamentTeam(rs.getLong("id"), rs.getLong("tournament_id"),
                        rs.getString("team_name"), rs.getLong("captain_id")));
            }
            return teams;
        }
    }

    private List<Tournament> collectTournaments(PreparedStatement ps) throws SQLException {
        List<Tournament> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapTournament(rs));
        }
        return list;
    }

    private Tournament mapTournament(ResultSet rs) throws SQLException {
        return new Tournament(
            rs.getLong("id"), rs.getString("name"), rs.getString("game_type"),
            rs.getString("status"), rs.getInt("max_teams"), rs.getInt("current_teams"),
            rs.getString("start_at"), rs.getString("created_at"));
    }
}
