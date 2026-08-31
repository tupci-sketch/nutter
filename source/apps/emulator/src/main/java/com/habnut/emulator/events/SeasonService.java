package com.habnut.emulator.events;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SeasonService {

    private static final Logger log = LoggerFactory.getLogger(SeasonService.class);

    public record Season(long id, String name, String slug, String startsAt, String endsAt,
                         String description, boolean active) {}
    public record SeasonProgress(long seasonId, long userId, int points, boolean claimedRewards) {}

    private final DatabaseManager db;

    public SeasonService(DatabaseManager db) {
        this.db = db;
    }

    public Optional<Season> getActive() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, name, slug, starts_at, ends_at, description, active" +
                 " FROM habnut_seasons WHERE active=1 AND starts_at<=NOW() AND ends_at>NOW()" +
                 " ORDER BY starts_at DESC LIMIT 1")) {
            List<Season> list = mapSeasons(ps);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }
    }

    public List<Season> listAll() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, name, slug, starts_at, ends_at, description, active" +
                 " FROM habnut_seasons ORDER BY starts_at DESC LIMIT 20")) {
            return mapSeasons(ps);
        }
    }

    public long create(String name, String slug, String startsAt, String endsAt,
                       String description) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_seasons (name, slug, starts_at, ends_at, description)" +
                 " VALUES (?,?,?,?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name); ps.setString(2, slug);
            ps.setString(3, startsAt); ps.setString(4, endsAt);
            ps.setString(5, description != null ? description : "");
            ps.executeUpdate();
            try (ResultSet gk = ps.getGeneratedKeys()) { gk.next(); return gk.getLong(1); }
        }
    }

    public boolean activate(long seasonId) throws SQLException {
        try (Connection conn = db.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                     "UPDATE habnut_seasons SET active=0")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                     "UPDATE habnut_seasons SET active=1 WHERE id=?")) {
                ps.setLong(1, seasonId);
                return ps.executeUpdate() > 0;
            }
        }
    }

    public void addPoints(long seasonId, long userId, int points) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_season_participants (season_id, user_id, points)" +
                 " VALUES (?,?,?) ON DUPLICATE KEY UPDATE points=points+?")) {
            ps.setLong(1, seasonId); ps.setLong(2, userId);
            ps.setInt(3, points); ps.setInt(4, points);
            ps.executeUpdate();
        }
    }

    public Optional<SeasonProgress> getProgress(long seasonId, long userId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT season_id, user_id, points, claimed_rewards" +
                 " FROM habnut_season_participants WHERE season_id=? AND user_id=?")) {
            ps.setLong(1, seasonId); ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new SeasonProgress(
                    rs.getLong("season_id"), rs.getLong("user_id"),
                    rs.getInt("points"), rs.getBoolean("claimed_rewards")));
            }
        }
    }

    public boolean claimRewards(long seasonId, long userId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_season_participants SET claimed_rewards=1" +
                 " WHERE season_id=? AND user_id=? AND claimed_rewards=0")) {
            ps.setLong(1, seasonId); ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<SeasonProgress> getLeaderboard(long seasonId, int limit) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT season_id, user_id, points, claimed_rewards" +
                 " FROM habnut_season_participants WHERE season_id=?" +
                 " ORDER BY points DESC LIMIT ?")) {
            ps.setLong(1, seasonId); ps.setInt(2, Math.min(limit, 100));
            List<SeasonProgress> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new SeasonProgress(rs.getLong("season_id"), rs.getLong("user_id"),
                        rs.getInt("points"), rs.getBoolean("claimed_rewards")));
                }
            }
            return list;
        }
    }

    private List<Season> mapSeasons(PreparedStatement ps) throws SQLException {
        List<Season> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Season(rs.getLong("id"), rs.getString("name"),
                    rs.getString("slug"), rs.getString("starts_at"), rs.getString("ends_at"),
                    rs.getString("description"), rs.getBoolean("active")));
            }
        }
        return list;
    }
}
