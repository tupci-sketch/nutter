package com.habnut.emulator.marketplace;

import com.habnut.emulator.db.DatabaseManager;
import com.habnut.emulator.economy.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MarketplaceService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceService.class);

    public record Listing(long id, long sellerId, long inventoryItemId, long baseId,
                          String spriteId, String name, int priceCredits,
                          String createdAt, String expiresAt, boolean sold) {}

    private final DatabaseManager db;
    private final TransactionService transactions;

    public MarketplaceService(DatabaseManager db, TransactionService transactions) {
        this.db           = db;
        this.transactions = transactions;
    }

    public List<Listing> search(String query, int limit, int offset) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT ml.id, ml.seller_id, ml.inventory_item_id, ml.base_id, " +
                 "ib.sprite_id, ib.name, ml.price_credits, ml.created_at, ml.expires_at, ml.sold " +
                 "FROM habnut_marketplace_listings ml " +
                 "JOIN habnut_items_base ib ON ib.id = ml.base_id " +
                 "WHERE ml.sold = 0 AND (ml.expires_at IS NULL OR ml.expires_at > NOW()) " +
                 "AND (? = '' OR ib.name LIKE ?) " +
                 "ORDER BY ml.price_credits ASC LIMIT ? OFFSET ?")) {
            String q = query.trim();
            ps.setString(1, q); ps.setString(2, "%" + q + "%");
            ps.setInt(3, limit); ps.setInt(4, offset);
            return mapList(ps);
        } catch (SQLException e) {
            log.error("Marketplace search failed", e);
            return List.of();
        }
    }

    public List<Listing> getMyListings(long userId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT ml.id, ml.seller_id, ml.inventory_item_id, ml.base_id, " +
                 "ib.sprite_id, ib.name, ml.price_credits, ml.created_at, ml.expires_at, ml.sold " +
                 "FROM habnut_marketplace_listings ml " +
                 "JOIN habnut_items_base ib ON ib.id = ml.base_id " +
                 "WHERE ml.seller_id = ? AND ml.sold = 0 ORDER BY ml.created_at DESC")) {
            ps.setLong(1, userId);
            return mapList(ps);
        } catch (SQLException e) {
            log.error("getMyListings failed for user {}", userId, e);
            return List.of();
        }
    }

    public long createListing(long sellerId, long inventoryItemId, long baseId,
                              int priceCredits, int durationHours) throws SQLException {
        if (priceCredits < 1) throw new IllegalArgumentException("Price must be positive");
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            // Remove from inventory
            try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM habnut_items_inventory WHERE id = ? AND owner_id = ?")) {
                del.setLong(1, inventoryItemId); del.setLong(2, sellerId);
                if (del.executeUpdate() == 0) {
                    conn.rollback();
                    throw new IllegalStateException("Item not owned by seller");
                }
            }
            long listingId;
            try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO habnut_marketplace_listings " +
                "(seller_id, inventory_item_id, base_id, price_credits, " +
                "expires_at) VALUES (?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL ? HOUR))",
                Statement.RETURN_GENERATED_KEYS)) {
                ins.setLong(1, sellerId); ins.setLong(2, inventoryItemId);
                ins.setLong(3, baseId); ins.setInt(4, priceCredits);
                ins.setInt(5, durationHours);
                ins.executeUpdate();
                try (ResultSet rs = ins.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("No key");
                    listingId = rs.getLong(1);
                }
            }
            conn.commit();
            return listingId;
        }
    }

    public record BuyResult(boolean success, String error) {}

    public BuyResult buy(long buyerId, long listingId) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            // Lock listing row
            Listing listing;
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT ml.id, ml.seller_id, ml.inventory_item_id, ml.base_id, " +
                "ib.sprite_id, ib.name, ml.price_credits, ml.created_at, ml.expires_at, ml.sold " +
                "FROM habnut_marketplace_listings ml JOIN habnut_items_base ib ON ib.id = ml.base_id " +
                "WHERE ml.id = ? AND ml.sold = 0 AND " +
                "(ml.expires_at IS NULL OR ml.expires_at > NOW()) FOR UPDATE")) {
                ps.setLong(1, listingId);
                List<Listing> l = mapList(ps);
                if (l.isEmpty()) { conn.rollback(); return new BuyResult(false, "Listing unavailable"); }
                listing = l.get(0);
            }
            if (listing.sellerId() == buyerId) {
                conn.rollback(); return new BuyResult(false, "Cannot buy own listing");
            }
            // Mark sold
            try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE habnut_marketplace_listings SET sold = 1, buyer_id = ? WHERE id = ?")) {
                upd.setLong(1, buyerId); upd.setLong(2, listingId);
                upd.executeUpdate();
            }
            // Transfer item to buyer
            try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO habnut_items_inventory (owner_id, base_id) VALUES (?, ?)")) {
                ins.setLong(1, buyerId); ins.setLong(2, listing.baseId());
                ins.executeUpdate();
            }
            conn.commit();

            // Debit buyer and credit seller outside the row-lock
            String idempKey = UUID.randomUUID().toString();
            transactions.debit(buyerId, TransactionService.Currency.CREDITS,
                listing.priceCredits(), "marketplace_buy:" + listingId, idempKey);
            transactions.grant(listing.sellerId(), TransactionService.Currency.CREDITS,
                listing.priceCredits(), "marketplace_sale:" + listingId, UUID.randomUUID().toString());

            log.info("Marketplace buy: listing={} buyer={} seller={} price={}",
                listingId, buyerId, listing.sellerId(), listing.priceCredits());
            return new BuyResult(true, null);
        } catch (Exception e) {
            log.error("Marketplace buy failed: listingId={} buyerId={}", listingId, buyerId, e);
            return new BuyResult(false, e.getMessage());
        }
    }

    public boolean cancelListing(long sellerId, long listingId) throws SQLException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            long baseId;
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT base_id FROM habnut_marketplace_listings " +
                "WHERE id = ? AND seller_id = ? AND sold = 0 FOR UPDATE")) {
                ps.setLong(1, listingId); ps.setLong(2, sellerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { conn.rollback(); return false; }
                    baseId = rs.getLong("base_id");
                }
            }
            try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM habnut_marketplace_listings WHERE id = ?")) {
                del.setLong(1, listingId); del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO habnut_items_inventory (owner_id, base_id) VALUES (?, ?)")) {
                ins.setLong(1, sellerId); ins.setLong(2, baseId); ins.executeUpdate();
            }
            conn.commit();
            return true;
        }
    }

    private List<Listing> mapList(PreparedStatement ps) throws SQLException {
        List<Listing> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new Listing(
                    rs.getLong("id"), rs.getLong("seller_id"),
                    rs.getLong("inventory_item_id"), rs.getLong("base_id"),
                    rs.getString("sprite_id"), rs.getString("name"),
                    rs.getInt("price_credits"),
                    rs.getTimestamp("created_at").toString(),
                    rs.getTimestamp("expires_at") != null ? rs.getTimestamp("expires_at").toString() : null,
                    rs.getBoolean("sold")));
            }
        }
        return result;
    }
}
