package com.habnut.emulator.rp;

import com.habnut.emulator.db.DatabaseManager;
import com.habnut.emulator.net.SessionRegistry;

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

    /** True when this character leads the faction. */
    public boolean isLeader(long factionId, long charId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT 1 FROM habnut_rp_factions WHERE id = ? AND leader_id = ?")) {
            ps.setLong(1, factionId);
            ps.setLong(2, charId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * The user ids behind a faction's members.
     *
     * Characters belong to users, and it is the user who has a connection, so
     * anything that wants to reach a faction has to cross that boundary here.
     */
    public List<Long> onlineMemberUserIds(long factionId) throws SQLException {
        List<Long> userIds = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT c.user_id FROM habnut_rp_faction_members m "
                 + "JOIN habnut_rp_characters c ON c.id = m.character_id "
                 + "WHERE m.faction_id = ?")) {
            ps.setLong(1, factionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) userIds.add(rs.getLong("user_id"));
            }
        }
        return userIds;
    }

    /**
     * How many members of the factions with a given tag are connected.
     *
     * Used to decide whether the city is policed enough for a bank to be worth
     * robbing. Counts connections rather than memberships, because a police
     * force that is all offline is not policing anything.
     */
    public int onlineMemberCount(String tag, SessionRegistry sessions) throws SQLException {
        int online = 0;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT DISTINCT c.user_id FROM habnut_rp_faction_members m "
                 + "JOIN habnut_rp_factions f ON f.id = m.faction_id "
                 + "JOIN habnut_rp_characters c ON c.id = m.character_id "
                 + "WHERE f.tag = ?")) {
            ps.setString(1, tag);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (sessions.byUserId(rs.getLong("user_id")).isPresent()) online++;
                }
            }
        }
        return online;
    }
}
