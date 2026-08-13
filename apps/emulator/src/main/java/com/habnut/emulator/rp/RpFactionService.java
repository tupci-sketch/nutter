package com.habnut.emulator.rp;

import com.habnut.emulator.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RpFactionService {

    public record Faction(long id, String name, String tag, String description,
                          int maxMembers, boolean isRecruiting, String badgeId,
                          Long leaderId, Long hqRoomId) {}

    public record Member(long factionId, long characterId, String rank, String joinedAt) {}

    private final DatabaseManager db;

    public RpFactionService(DatabaseManager db) {
        this.db = db;
    }

    public List<Faction> listAll() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, name, tag, description, max_members, is_recruiting," +
                 " badge_id, leader_id, hq_room_id FROM habnut_rp_factions ORDER BY id")) {
            return mapFactions(ps);
        }
    }

    public Optional<Faction> findById(long factionId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, name, tag, description, max_members, is_recruiting," +
                 " badge_id, leader_id, hq_room_id FROM habnut_rp_factions WHERE id=?")) {
            ps.setLong(1, factionId);
            List<Faction> list = mapFactions(ps);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }
    }

    public int getMemberCount(long factionId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM habnut_rp_faction_members WHERE faction_id=?")) {
            ps.setLong(1, factionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public boolean join(long factionId, long charId) throws SQLException {
        Optional<Faction> opt = findById(factionId);
        if (opt.isEmpty() || !opt.get().isRecruiting()) return false;
        if (getMemberCount(factionId) >= opt.get().maxMembers()) return false;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT IGNORE INTO habnut_rp_faction_members (faction_id, character_id, rank)" +
                 " VALUES (?,?,'recruit')")) {
            ps.setLong(1, factionId);
            ps.setLong(2, charId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean leave(long factionId, long charId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM habnut_rp_faction_members WHERE faction_id=? AND character_id=?")) {
            ps.setLong(1, factionId);
            ps.setLong(2, charId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<Member> getMembers(long factionId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT faction_id, character_id, rank, joined_at" +
                 " FROM habnut_rp_faction_members WHERE faction_id=?")) {
            ps.setLong(1, factionId);
            List<Member> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Member(rs.getLong("faction_id"),
                        rs.getLong("character_id"), rs.getString("rank"),
                        rs.getString("joined_at")));
                }
            }
            return list;
        }
    }

    private List<Faction> mapFactions(PreparedStatement ps) throws SQLException {
        List<Faction> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Object lid = rs.getObject("leader_id");
                Object hqid = rs.getObject("hq_room_id");
                list.add(new Faction(rs.getLong("id"), rs.getString("name"),
                    rs.getString("tag"), rs.getString("description"),
                    rs.getInt("max_members"), rs.getBoolean("is_recruiting"),
                    rs.getString("badge_id"),
                    lid != null ? ((Number) lid).longValue() : null,
                    hqid != null ? ((Number) hqid).longValue() : null));
            }
        }
        return list;
    }
}
