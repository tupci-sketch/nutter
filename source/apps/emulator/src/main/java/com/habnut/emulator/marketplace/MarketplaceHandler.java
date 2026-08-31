package com.habnut.emulator.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.RateLimiter;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.protocol.ErrorCode;
import com.habnut.emulator.protocol.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public final class MarketplaceHandler {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceHandler.class);
    private static final int MAX_SEARCH_LIMIT = 50;
    private static final int DEFAULT_SEARCH_LIMIT = 20;
    private static final int MAX_DURATION_HOURS = 72;
    private static final int MIN_DURATION_HOURS = 1;

    private final MarketplaceService marketplace;
    private final PacketRouter router;
    private final RateLimiter rateLimiter;

    public MarketplaceHandler(MarketplaceService marketplace, PacketRouter router,
                              RateLimiter rateLimiter) {
        this.marketplace = marketplace;
        this.router      = router;
        this.rateLimiter = rateLimiter;
    }

    public void register(PacketRouter router) {
        router.register(PacketType.MKT_SEARCH,         this::handleSearch);
        router.register(PacketType.MKT_LISTING_CREATE, this::handleCreate);
        router.register(PacketType.MKT_LISTING_CANCEL, this::handleCancel);
        router.register(PacketType.MKT_LISTING_BUY,    this::handleBuy);
        router.register(PacketType.MKT_MY_LISTINGS,    this::handleMyListings);
    }

    private void handleSearch(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        String query = payload.path("query").asText("").trim();
        int limit  = Math.min(payload.path("limit").asInt(DEFAULT_SEARCH_LIMIT), MAX_SEARCH_LIMIT);
        int offset = Math.max(payload.path("offset").asInt(0), 0);
        List<MarketplaceService.Listing> results = marketplace.search(query, limit, offset);
        session.send(router.buildPacket(PacketType.MKT_SEARCH_RESULT,
            Map.of("query", query, "offset", offset, "listings", results)));
    }

    private void handleCreate(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        if (!rateLimiter.tryAcquire(session.sessionId, RateLimiter.Bucket.CATALOGUE_PURCHASE)) {
            sendError(session, ErrorCode.GENERIC_RATE_LIMITED, "Too many requests"); return;
        }
        long inventoryItemId = payload.path("inventoryItemId").asLong(-1);
        long baseId          = payload.path("baseId").asLong(-1);
        int  priceCredits    = payload.path("priceCredits").asInt(0);
        int  durationHours   = Math.min(Math.max(
            payload.path("durationHours").asInt(48), MIN_DURATION_HOURS), MAX_DURATION_HOURS);

        if (inventoryItemId < 1 || baseId < 1 || priceCredits < 1) {
            sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid listing parameters"); return;
        }
        try {
            long listingId = marketplace.createListing(session.getUserId(), inventoryItemId,
                baseId, priceCredits, durationHours);
            session.send(router.buildPacket(PacketType.MKT_LISTING_CREATED,
                Map.of("listingId", listingId)));
        } catch (IllegalArgumentException e) {
            sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, e.getMessage());
        } catch (IllegalStateException e) {
            sendError(session, ErrorCode.FURNI_NOT_IN_INVENTORY, e.getMessage());
        } catch (SQLException e) {
            log.error("Marketplace create failed for user {}", session.getUserId(), e);
            sendError(session, ErrorCode.GENERIC_INTERNAL_ERROR, "Failed to create listing");
        }
    }

    private void handleCancel(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long listingId = payload.path("listingId").asLong(-1);
        if (listingId < 1) {
            sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid listing id"); return;
        }
        try {
            boolean cancelled = marketplace.cancelListing(session.getUserId(), listingId);
            if (!cancelled) {
                sendError(session, ErrorCode.GENERIC_NOT_FOUND, "Listing not found or not yours"); return;
            }
            session.send(router.buildPacket(PacketType.MKT_LISTING_EXPIRED,
                Map.of("listingId", listingId)));
        } catch (SQLException e) {
            log.error("Marketplace cancel failed: listingId={}", listingId, e);
            sendError(session, ErrorCode.GENERIC_INTERNAL_ERROR, "Failed to cancel listing");
        }
    }

    private void handleBuy(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        if (!rateLimiter.tryAcquire(session.sessionId, RateLimiter.Bucket.CATALOGUE_PURCHASE)) {
            sendError(session, ErrorCode.GENERIC_RATE_LIMITED, "Too many requests"); return;
        }
        long listingId = payload.path("listingId").asLong(-1);
        if (listingId < 1) {
            sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid listing id"); return;
        }
        MarketplaceService.BuyResult result = marketplace.buy(session.getUserId(), listingId);
        if (result.success()) {
            session.send(router.buildPacket(PacketType.MKT_LISTING_BOUGHT,
                Map.of("listingId", listingId)));
        } else {
            sendError(session, ErrorCode.ECO_TRANSACTION_FAILED, result.error());
        }
    }

    private void handleMyListings(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        List<MarketplaceService.Listing> listings = marketplace.getMyListings(session.getUserId());
        session.send(router.buildPacket(PacketType.MKT_MY_LISTINGS_RESULT,
            Map.of("listings", listings)));
    }

    private void sendError(WebSocketSession session, String code, String msg) {
        session.send(router.buildPacket("marketplace.error",
            Map.of("code", code, "message", msg)));
    }
}
