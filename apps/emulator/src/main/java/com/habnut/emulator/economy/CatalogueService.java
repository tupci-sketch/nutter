package com.habnut.emulator.economy;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CatalogueService {

    private static final Logger log = LoggerFactory.getLogger(CatalogueService.class);

    public record CatPage(long id, String name, String layout, int rank,
                          boolean visible, List<CatItem> items) {}

    public record CatItem(long id, long pageId, long baseId, String name,
                          String description, int creditsPrice, int diamondsPrice,
                          int limitedTotal, int limitedSold, boolean isGift) {}

    private final DatabaseManager db;
    private final TransactionService transactions;
    private final InventoryService inventory;

    public CatalogueService(DatabaseManager db, TransactionService transactions,
                            InventoryService inventory) {
        this.db           = db;
        this.transactions = transactions;
        this.inventory    = inventory;
    }

    public List<CatPage> getPages(int minRank) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, name, layout, rank, is_visible " +
                 "FROM habnut_catalogue_pages WHERE is_visible = 1 AND rank <= ? " +
                 "ORDER BY rank, name")) {
            ps.setInt(1, minRank);
            List<CatPage> pages = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pages.add(new CatPage(
                        rs.getLong("id"), rs.getString("name"),
                        rs.getString("layout"), rs.getInt("rank"),
                        rs.getBoolean("is_visible"), List.of()));
                }
            }
            return pages;
        } catch (SQLException e) {
            log.error("getPages failed", e);
            return List.of();
        }
    }

    public CatPage getPage(long pageId, int minRank) {
        try (Connection conn = db.getConnection()) {
            CatPage page;
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, name, layout, rank, is_visible FROM habnut_catalogue_pages " +
                "WHERE id = ? AND rank <= ?")) {
                ps.setLong(1, pageId); ps.setInt(2, minRank);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    page = new CatPage(rs.getLong("id"), rs.getString("name"),
                        rs.getString("layout"), rs.getInt("rank"),
                        rs.getBoolean("is_visible"), new ArrayList<>());
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT ci.id, ci.page_id, ci.base_id, ci.name, ci.description, " +
                "ci.credits_price, ci.diamonds_price, ci.limited_total, ci.limited_sold, ci.is_gift " +
                "FROM habnut_catalogue_offers ci WHERE ci.page_id = ? AND ci.is_visible = 1")) {
                ps.setLong(1, pageId);
                List<CatItem> items = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(new CatItem(
                            rs.getLong("id"), rs.getLong("page_id"), rs.getLong("base_id"),
                            rs.getString("name"), rs.getString("description"),
                            rs.getInt("credits_price"), rs.getInt("diamonds_price"),
                            rs.getInt("limited_total"), rs.getInt("limited_sold"),
                            rs.getBoolean("is_gift")));
                    }
                }
                return new CatPage(page.id(), page.name(), page.layout(), page.rank(),
                    page.visible(), items);
            }
        } catch (SQLException e) {
            log.error("getPage failed for page {}", pageId, e);
            return null;
        }
    }

    public record PurchaseResult(boolean success, String error, long inventoryItemId,
                                 long newCredits, long newDiamonds) {}

    public PurchaseResult purchase(long userId, long catalogueItemId, int userRank) {
        try (Connection conn = db.getConnection()) {
            CatItem item = findItem(conn, catalogueItemId);
            if (item == null) return fail("Item not found");

            CatPage page = findPageForItem(conn, item.pageId());
            if (page == null || page.rank() > userRank) return fail("Access denied");

            if (item.limitedTotal() > 0) {
                int updated = reserveLimited(conn, catalogueItemId, item.limitedTotal());
                if (updated == 0) return fail("Sold out");
            }

            String idempKey = UUID.randomUUID().toString();
            if (item.creditsPrice() > 0) {
                transactions.debit(userId, TransactionService.Currency.CREDITS,
                    item.creditsPrice(), "catalogue_purchase:" + catalogueItemId, idempKey);
            }
            if (item.diamondsPrice() > 0) {
                transactions.debit(userId, TransactionService.Currency.DIAMONDS,
                    item.diamondsPrice(), "catalogue_purchase:" + catalogueItemId, idempKey);
            }

            long invId = inventory.grantItem(userId, item.baseId());
            TransactionService.Balance bal = transactions.getBalance(userId);
            return new PurchaseResult(true, null, invId, bal.credits(), bal.diamonds());
        } catch (IllegalStateException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            log.error("Purchase failed userId={} itemId={}", userId, catalogueItemId, e);
            return fail("Server error");
        }
    }

    private int reserveLimited(Connection conn, long itemId, int total) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "UPDATE habnut_catalogue_offers SET limited_sold = limited_sold + 1 " +
            "WHERE id = ? AND limited_sold < ?")) {
            ps.setLong(1, itemId); ps.setInt(2, total);
            return ps.executeUpdate();
        }
    }

    private CatItem findItem(Connection conn, long itemId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT * FROM habnut_catalogue_offers WHERE id = ?")) {
            ps.setLong(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new CatItem(rs.getLong("id"), rs.getLong("page_id"), rs.getLong("base_id"),
                    rs.getString("name"), rs.getString("description"),
                    rs.getInt("credits_price"), rs.getInt("diamonds_price"),
                    rs.getInt("limited_total"), rs.getInt("limited_sold"), rs.getBoolean("is_gift"));
            }
        }
    }

    private CatPage findPageForItem(Connection conn, long pageId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT id, name, layout, rank, is_visible FROM habnut_catalogue_pages WHERE id = ?")) {
            ps.setLong(1, pageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new CatPage(rs.getLong("id"), rs.getString("name"),
                    rs.getString("layout"), rs.getInt("rank"),
                    rs.getBoolean("is_visible"), List.of());
            }
        }
    }

    private PurchaseResult fail(String reason) {
        return new PurchaseResult(false, reason, 0, 0, 0);
    }
}
