package com.habnut.emulator.rp;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Armed conflict between Nutropolis characters.
 *
 * The server owns every part of an exchange: the client asks to attack a
 * target, and the server decides whether the attacker holds the weapon, whether
 * the cooldown has elapsed, how much damage lands, and whether the victim goes
 * down. A downed character is incapacitated rather than removed, so medical and
 * arrest flows have something to act on.
 */
public final class RpCombatService {

    private static final Logger log = LoggerFactory.getLogger(RpCombatService.class);

    /** How long a downed character stays down before they can be revived. */
    public static final long DOWN_DURATION_MS = 120_000;

    public record Weapon(int id, String code, String name, String category,
                         int damage, int rangeTiles, int cooldownMs,
                         int magazineSize, boolean licenceRequired, int price) {}

    /** The outcome of one attack, as the server resolved it. */
    public record AttackResult(boolean landed, String rejection, int damage,
                               int victimHealthAfter, boolean fatal) {

        static AttackResult rejected(String reason) {
            return new AttackResult(false, reason, 0, 0, false);
        }
    }

    private final DatabaseManager db;

    public RpCombatService(DatabaseManager db) {
        this.db = db;
    }

    // ─── weapons ────────────────────────────────────────────────────────────

    public List<Weapon> catalogue() {
        List<Weapon> weapons = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, code, name, category, damage, range_tiles, cooldown_ms, " +
                 "magazine_size, licence_required, price_rp_cash " +
                 "FROM habnut_rp_weapon_types ORDER BY price_rp_cash");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) weapons.add(mapWeapon(rs));
        } catch (SQLException e) {
            log.error("weapon catalogue failed", e);
        }
        return weapons;
    }

    /** Weapons a character is carrying, with the equipped one first. */
    public List<Weapon> inventory(long characterId) {
        List<Weapon> weapons = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT w.id, w.code, w.name, w.category, w.damage, w.range_tiles, " +
                 "w.cooldown_ms, w.magazine_size, w.licence_required, w.price_rp_cash " +
                 "FROM habnut_rp_character_weapons cw " +
                 "JOIN habnut_rp_weapon_types w ON w.id = cw.weapon_id " +
                 "WHERE cw.character_id = ? ORDER BY cw.equipped DESC, w.name")) {
            ps.setLong(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) weapons.add(mapWeapon(rs));
            }
        } catch (SQLException e) {
            log.error("weapon inventory failed for character {}", characterId, e);
        }
        return weapons;
    }

    /** Equips one weapon, unequipping whatever the character held before. */
    public boolean equip(long characterId, int weaponId) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement clear = conn.prepareStatement(
                     "UPDATE habnut_rp_character_weapons SET equipped = 0 WHERE character_id = ?")) {
                clear.setLong(1, characterId);
                clear.executeUpdate();
            }
            try (PreparedStatement set = conn.prepareStatement(
                     "UPDATE habnut_rp_character_weapons SET equipped = 1 " +
                     "WHERE character_id = ? AND weapon_id = ?")) {
                set.setLong(1, characterId);
                set.setInt(2, weaponId);
                if (set.executeUpdate() == 0) {
                    conn.rollback();
                    return false;
                }
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            log.error("equip failed for character {} weapon {}", characterId, weaponId, e);
            return false;
        }
    }

    // ─── attacking ──────────────────────────────────────────────────────────

    /**
     * Resolves an attack from one character on another.
     *
     * Every check is made against stored state rather than anything the client
     * supplied: the attacker must hold the weapon, be off cooldown, and be
     * standing; the victim must be alive and in range.
     */
    public AttackResult attack(long attackerId, long victimId, int distanceTiles, Long roomId) {
        if (attackerId == victimId) return AttackResult.rejected("You cannot attack yourself");

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            if (isDowned(conn, attackerId)) {
                conn.rollback();
                return AttackResult.rejected("You are down and cannot attack");
            }

            Optional<Weapon> held = equippedWeapon(conn, attackerId);
            if (held.isEmpty()) {
                conn.rollback();
                return AttackResult.rejected("You are not carrying a weapon");
            }
            Weapon weapon = held.get();

            if (distanceTiles > weapon.rangeTiles()) {
                conn.rollback();
                return AttackResult.rejected("Target is out of range");
            }
            if (!cooldownElapsed(conn, attackerId, weapon.cooldownMs())) {
                conn.rollback();
                return AttackResult.rejected("Weapon is not ready");
            }

            // Lock the victim so two attackers cannot both read the same health
            // and each believe they landed the finishing blow.
            int health;
            try (PreparedStatement lock = conn.prepareStatement(
                     "SELECT health, is_downed FROM habnut_rp_characters WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, victimId);
                try (ResultSet rs = lock.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return AttackResult.rejected("Target not found");
                    }
                    if (rs.getBoolean("is_downed")) {
                        conn.rollback();
                        return AttackResult.rejected("Target is already down");
                    }
                    health = rs.getInt("health");
                }
            }

            int damage = weapon.damage();
            int remaining = Math.max(0, health - damage);
            boolean fatal = remaining == 0;

            try (PreparedStatement upd = conn.prepareStatement(
                     "UPDATE habnut_rp_characters SET health = ?, is_downed = ?, " +
                     "downed_at = CASE WHEN ? THEN NOW() ELSE downed_at END WHERE id = ?")) {
                upd.setInt(1, remaining);
                upd.setBoolean(2, fatal);
                upd.setBoolean(3, fatal);
                upd.setLong(4, victimId);
                upd.executeUpdate();
            }

            try (PreparedStatement touch = conn.prepareStatement(
                     "UPDATE habnut_rp_characters SET last_attack_at = NOW() WHERE id = ?")) {
                touch.setLong(1, attackerId);
                touch.executeUpdate();
            }

            try (PreparedStatement logRow = conn.prepareStatement(
                     "INSERT INTO habnut_rp_combat_log " +
                     "(attacker_id, victim_id, weapon_id, room_id, damage, " +
                     "victim_health_after, was_fatal) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                logRow.setLong(1, attackerId);
                logRow.setLong(2, victimId);
                logRow.setInt(3, weapon.id());
                if (roomId != null) logRow.setLong(4, roomId); else logRow.setNull(4, Types.INTEGER);
                logRow.setInt(5, damage);
                logRow.setInt(6, remaining);
                logRow.setBoolean(7, fatal);
                logRow.executeUpdate();
            }

            conn.commit();
            return new AttackResult(true, null, damage, remaining, fatal);

        } catch (SQLException e) {
            log.error("attack failed: attacker={} victim={}", attackerId, victimId, e);
            return AttackResult.rejected("Attack could not be resolved");
        }
    }

    /** Brings a downed character back with the given health. */
    public boolean revive(long characterId, int health) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_rp_characters SET health = ?, is_downed = 0, downed_at = NULL " +
                 "WHERE id = ? AND is_downed = 1")) {
            ps.setInt(1, Math.clamp(health, 1, 100));
            ps.setLong(2, characterId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("revive failed for character {}", characterId, e);
            return false;
        }
    }

    /** True once a downed character has waited out the respawn timer. */
    public boolean canSelfRevive(long characterId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT downed_at FROM habnut_rp_characters WHERE id = ? AND is_downed = 1")) {
            ps.setLong(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                Timestamp downedAt = rs.getTimestamp("downed_at");
                return downedAt != null
                    && System.currentTimeMillis() - downedAt.getTime() >= DOWN_DURATION_MS;
            }
        } catch (SQLException e) {
            log.error("canSelfRevive failed for character {}", characterId, e);
            return false;
        }
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private boolean isDowned(Connection conn, long characterId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT is_downed FROM habnut_rp_characters WHERE id = ?")) {
            ps.setLong(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean("is_downed");
            }
        }
    }

    private Optional<Weapon> equippedWeapon(Connection conn, long characterId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT w.id, w.code, w.name, w.category, w.damage, w.range_tiles, " +
                 "w.cooldown_ms, w.magazine_size, w.licence_required, w.price_rp_cash " +
                 "FROM habnut_rp_character_weapons cw " +
                 "JOIN habnut_rp_weapon_types w ON w.id = cw.weapon_id " +
                 "WHERE cw.character_id = ? AND cw.equipped = 1")) {
            ps.setLong(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapWeapon(rs)) : Optional.empty();
            }
        }
    }

    private boolean cooldownElapsed(Connection conn, long characterId, int cooldownMs)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT last_attack_at FROM habnut_rp_characters WHERE id = ?")) {
            ps.setLong(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                Timestamp last = rs.getTimestamp("last_attack_at");
                return last == null
                    || System.currentTimeMillis() - last.getTime() >= cooldownMs;
            }
        }
    }

    private static Weapon mapWeapon(ResultSet rs) throws SQLException {
        return new Weapon(
            rs.getInt("id"), rs.getString("code"), rs.getString("name"),
            rs.getString("category"), rs.getInt("damage"), rs.getInt("range_tiles"),
            rs.getInt("cooldown_ms"), rs.getInt("magazine_size"),
            rs.getBoolean("licence_required"), rs.getInt("price_rp_cash"));
    }
}
