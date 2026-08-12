package com.habnut.emulator.economy;

import com.fasterxml.jackson.databind.JsonNode;
import com.habnut.emulator.auth.UserRepository;
import com.habnut.emulator.metrics.MetricsRegistry;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.RateLimiter;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.protocol.ErrorCode;
import com.habnut.emulator.protocol.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class EconomyHandler {

    private static final Logger log = LoggerFactory.getLogger(EconomyHandler.class);

    private final TransactionService transactions;
    private final InventoryService inventory;
    private final CatalogueService catalogue;
    private final PacketRouter router;
    private final RateLimiter rateLimiter;
    private final MetricsRegistry metrics;

    public EconomyHandler(TransactionService transactions, InventoryService inventory,
                          CatalogueService catalogue, PacketRouter router,
                          RateLimiter rateLimiter, MetricsRegistry metrics) {
        this.transactions = transactions;
        this.inventory    = inventory;
        this.catalogue    = catalogue;
        this.router       = router;
        this.rateLimiter  = rateLimiter;
        this.metrics      = metrics;
    }

    public void register(PacketRouter router) {
        router.register(PacketType.INVENTORY_LIST,   this::handleInventoryList);
        router.register(PacketType.INVENTORY_SEARCH, this::handleInventorySearch);
        router.register(PacketType.CAT_PAGES,        this::handleCatPages);
        router.register(PacketType.CAT_PAGE,         this::handleCatPage);
        router.register(PacketType.CAT_PURCHASE,     this::handleCatPurchase);
    }

    private void handleInventoryList(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        int page  = Math.max(0, payload.path("page").asInt(0));
        int limit = Math.min(50, Math.max(1, payload.path("limit").asInt(50)));
        int offset = page * limit;

        long userId = session.getUserId();
        List<InventoryService.InventoryItem> items = inventory.list(userId, offset, limit);
        int total = inventory.count(userId);

        List<Map<String, Object>> mapped = items.stream().map(i -> Map.<String, Object>of(
            "id", i.id(), "baseId", i.baseId(), "spriteId", i.spriteId(),
            "name", i.name(), "type", i.type()
        )).collect(Collectors.toList());

        session.send(router.buildPacket(PacketType.INVENTORY_LIST_RESULT,
            Map.of("items", mapped, "total", total, "page", page, "limit", limit)));
    }

    private void handleInventorySearch(WebSocketSession session, JsonNode payload) {
        handleInventoryList(session, payload);
    }

    private void handleCatPages(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        int rank = rankForSession(session);
        List<CatalogueService.CatPage> pages = catalogue.getPages(rank);
        List<Map<String, Object>> mapped = pages.stream().map(p -> Map.<String, Object>of(
            "id", p.id(), "name", p.name(), "layout", p.layout()
        )).collect(Collectors.toList());
        session.send(router.buildPacket(PacketType.CAT_PAGES_RESULT, Map.of("pages", mapped)));
    }

    private void handleCatPage(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long pageId = payload.path("pageId").asLong(-1);
        if (pageId < 1) return;
        int rank = rankForSession(session);
        CatalogueService.CatPage page = catalogue.getPage(pageId, rank);
        if (page == null) {
            sendError(session, ErrorCode.GENERIC_NOT_FOUND, "Page not found");
            return;
        }
        List<Map<String, Object>> items = page.items().stream().map(i -> Map.<String, Object>of(
            "id", i.id(), "baseId", i.baseId(), "name", i.name(),
            "description", i.description(), "creditsPrice", i.creditsPrice(),
            "diamondsPrice", i.diamondsPrice(),
            "limitedTotal", i.limitedTotal(), "limitedSold", i.limitedSold()
        )).collect(Collectors.toList());
        session.send(router.buildPacket(PacketType.CAT_PAGE_RESULT,
            Map.of("pageId", pageId, "name", page.name(), "layout", page.layout(), "items", items)));
    }

    private void handleCatPurchase(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        if (!rateLimiter.tryAcquire(session.sessionId, RateLimiter.Bucket.CATALOGUE_PURCHASE)) {
            sendError(session, ErrorCode.AUTH_RATE_LIMITED, "Purchase rate limited");
            return;
        }

        long userId = session.getUserId();
        long itemId = payload.path("itemId").asLong(-1);
        if (itemId < 1) { sendError(session, ErrorCode.GENERIC_INVALID_PACKET, "Invalid itemId"); return; }

        int rank = rankForSession(session);
        CatalogueService.PurchaseResult result = catalogue.purchase(userId, itemId, rank);

        if (!result.success()) {
            session.send(router.buildPacket(PacketType.CAT_PURCHASE_ERROR,
                Map.of("code", ErrorCode.ECO_INSUFFICIENT_BALANCE, "message", result.error())));
            return;
        }

        session.send(router.buildPacket(PacketType.CAT_PURCHASE_SUCCESS, Map.of(
            "inventoryItemId", result.inventoryItemId(),
            "credits", result.newCredits(),
            "diamonds", result.newDiamonds()
        )));
        metrics.incrementEconomyTransactions();
        log.info("Catalogue purchase: userId={} itemId={} invId={}",
            userId, itemId, result.inventoryItemId());
    }

    private int rankForSession(WebSocketSession session) {
        // Rank is embedded in session at auth time in Phase 4+; defaulting to 1 here.
        // Full rank tracking is wired in Phase 15 (staff/mod rank enforcement).
        return 1;
    }

    private void sendError(WebSocketSession session, String code, String msg) {
        session.send(router.buildPacket("system.error", Map.of("code", code, "message", msg)));
    }
}
