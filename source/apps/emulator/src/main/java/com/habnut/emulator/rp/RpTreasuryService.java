package com.habnut.emulator.rp;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Money a faction holds together.
 *
 * Territory income and heist takings land here, wages and fines come out of it,
 * and members can put their own money in. Every movement is written to a ledger
 * rather than only adjusting a balance: a faction's members will argue about
 * where the money went, and the answer should not depend on somebody's memory.
 *
 * This is roleplay money throughout. It never touches Credits or Diamonds, in
 * either direction.
 */
public final class RpTreasuryService {

    private static final Logger log = LoggerFactory.getLogger(RpTreasuryService.class);

    /** Where a movement came from. */
    public enum Kind {
        TURF_INCOME, HEIST, DEPOSIT, WITHDRAWAL, FINE, PAYROLL;

        String column() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record Entry(long id, int factionId, long amount, long balanceAfter,
                        String kind, String memo, Long actorCharacterId, String createdAt) {}

    public record Result(boolean ok, String reason, long balance) {}

    private final DatabaseManager db;

    public RpTreasuryService(DatabaseManager db) {
        this.db = db;
    }

    /** What a faction currently holds. */
    public long balanceOf(int factionId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT treasury FROM habnut_rp_factions WHERE id = ?")) {
            ps.setInt(1, factionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            log.error("balanceOf failed for faction {}", factionId, e);
            return 0L;
        }
    }

    /**
     * Adds to a faction's treasury.
     *
     * @param amount must be positive; a negative credit is a withdrawal and
     *               should say so, or the ledger stops meaning anything
     */
    public Result credit(int factionId, long amount, Kind kind, String memo, Long actorCharacterId) {
        if (amount <= 0) {
            return new Result(false, "amount_must_be_positive", balanceOf(factionId));
        }
        return move(factionId, amount, kind, memo, actorCharacterId);
    }

    /**
     * Takes from a faction's treasury.
     *
     * A faction cannot be taken below zero: a fine larger than the treasury
     * empties it rather than putting the faction in debt, because debt with no
     * way to repay it is just a faction that can never do anything again.
     */
    public Result debit(int factionId, long amount, Kind kind, String memo, Long actorCharacterId) {
        if (amount <= 0) {
            return new Result(false, "amount_must_be_positive", balanceOf(factionId));
        }
        return move(factionId, -amount, kind, memo, actorCharacterId);
    }

    /**
     * Applies one movement, atomically.
     *
     * The balance is read and written inside one transaction with the row
     * locked, so two heists resolving at the same moment cannot both read the
     * old balance and lose one of the payouts.
     */
    private Result move(int factionId, long delta, Kind kind, String memo, Long actor) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long balance;
                try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT treasury FROM habnut_rp_factions WHERE id = ? FOR UPDATE")) {
                    ps.setInt(1, factionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return new Result(false, "no_such_faction", 0L);
                        }
                        balance = rs.getLong(1);
                    }
                }

                long after = balance + delta;
                if (after < 0) {
                    if (kind != Kind.FINE) {
                        conn.rollback();
                        return new Result(false, "insufficient_funds", balance);
                    }
                    // A fine takes what there is.
                    delta = -balance;
                    after = 0;
                }

                try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE habnut_rp_factions SET treasury = ? WHERE id = ?")) {
                    ps.setLong(1, after);
                    ps.setInt(2, factionId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO habnut_rp_treasury_log "
                    + "(faction_id, amount, balance_after, kind, memo, actor_character_id) "
                    + "VALUES (?,?,?,?,?,?)")) {
                    ps.setInt(1, factionId);
                    ps.setLong(2, delta);
                    ps.setLong(3, after);
                    ps.setString(4, kind.column());
                    ps.setString(5, memo == null ? "" : memo);
                    if (actor == null) ps.setNull(6, java.sql.Types.INTEGER);
                    else ps.setLong(6, actor);
                    ps.executeUpdate();
                }

                conn.commit();
                return new Result(true, null, after);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("Treasury movement failed for faction {}", factionId, e);
            return new Result(false, "error", balanceOf(factionId));
        }
    }

    /** The most recent movements, newest first. */
    public List<Entry> history(int factionId, int limit) {
        List<Entry> entries = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, faction_id, amount, balance_after, kind, memo, "
                 + "actor_character_id, created_at FROM habnut_rp_treasury_log "
                 + "WHERE faction_id = ? ORDER BY created_at DESC, id DESC LIMIT ?")) {
            ps.setInt(1, factionId);
            ps.setInt(2, Math.max(1, Math.min(limit, 100)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new Entry(
                        rs.getLong("id"),
                        rs.getInt("faction_id"),
                        rs.getLong("amount"),
                        rs.getLong("balance_after"),
                        rs.getString("kind"),
                        rs.getString("memo"),
                        rs.getObject("actor_character_id") == null
                            ? null : rs.getLong("actor_character_id"),
                        String.valueOf(rs.getTimestamp("created_at"))));
                }
            }
        } catch (SQLException e) {
            log.error("Treasury history failed for faction {}", factionId, e);
        }
        return entries;
    }

    /**
     * Pays every faction the income from the territory it holds.
     *
     * Territory pays by the hour, and a turf records when it last paid, so a
     * restart — or two servers briefly overlapping — cannot be used to collect
     * the same hour twice.
     *
     * @return how much was paid out in total
     */
    public long payTerritoryIncome() {
        long total = 0;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT owner_faction_id, SUM(income_per_hour) AS due, "
                 + "GROUP_CONCAT(id) AS turf_ids FROM habnut_rp_turfs "
                 + "WHERE owner_faction_id IS NOT NULL AND income_per_hour > 0 "
                 + "AND (last_paid_at IS NULL "
                 + "     OR TIMESTAMPADD(HOUR, 1, last_paid_at) <= NOW()) "
                 + "GROUP BY owner_faction_id")) {

            List<int[]> payments = new ArrayList<>();
            List<String> turfIdLists = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    payments.add(new int[] { rs.getInt("owner_faction_id"), rs.getInt("due") });
                    turfIdLists.add(rs.getString("turf_ids"));
                }
            }

            for (int i = 0; i < payments.size(); i++) {
                int factionId = payments.get(i)[0];
                int due = payments.get(i)[1];
                if (due <= 0) continue;

                Result result = credit(factionId, due, Kind.TURF_INCOME,
                    "Income from territory", null);
                if (!result.ok()) continue;

                total += due;
                markPaid(conn, turfIdLists.get(i));
            }
        } catch (SQLException e) {
            log.error("Territory income payout failed", e);
        }
        return total;
    }

    /** Stamps the turfs that have just paid, so the next hour starts now. */
    private void markPaid(Connection conn, String turfIds) throws SQLException {
        if (turfIds == null || turfIds.isBlank()) return;

        // The ids come from the server's own aggregate, not from a player, but
        // they are still checked before going anywhere near a statement.
        for (String id : turfIds.split(",")) {
            int turfId;
            try {
                turfId = Integer.parseInt(id.trim());
            } catch (NumberFormatException e) {
                continue;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE habnut_rp_turfs SET last_paid_at = NOW() WHERE id = ?")) {
                ps.setInt(1, turfId);
                ps.executeUpdate();
            }
        }
    }
}
