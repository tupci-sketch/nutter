package com.habnut.emulator.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.SessionRegistry;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.protocol.ErrorCode;
import com.habnut.emulator.protocol.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class TradeHandler {

    private static final Logger log = LoggerFactory.getLogger(TradeHandler.class);

    private final TradeService tradeService;
    private final SessionRegistry sessions;
    private final PacketRouter router;

    public TradeHandler(TradeService tradeService, SessionRegistry sessions, PacketRouter router) {
        this.tradeService = tradeService;
        this.sessions     = sessions;
        this.router       = router;
    }

    public void register(PacketRouter router) {
        router.register(PacketType.TRADE_OPEN,         this::handleOpen);
        router.register(PacketType.TRADE_OFFER_ADD,    this::handleOfferAdd);
        router.register(PacketType.TRADE_OFFER_REMOVE, this::handleOfferRemove);
        router.register(PacketType.TRADE_CONFIRM,      this::handleConfirm);
        router.register(PacketType.TRADE_ACCEPT,       this::handleAccept);
        router.register(PacketType.TRADE_UNACCEPT,     this::handleUnaccept);
        router.register(PacketType.TRADE_CANCEL,       this::handleCancel);
    }

    private void handleOpen(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long userId   = session.getUserId();
        long partnerId = payload.path("partnerId").asLong(-1);
        if (partnerId < 1 || partnerId == userId) {
            sendError(session, ErrorCode.TRADE_INVALID_PARTNER, "Invalid partner"); return;
        }
        WebSocketSession partnerSession = sessions.byUserId(partnerId).orElse(null);
        if (partnerSession == null || !partnerSession.isAuthenticated()) {
            sendError(session, ErrorCode.TRADE_INVALID_PARTNER, "Partner not online"); return;
        }

        TradeSession trade;
        try {
            trade = tradeService.open(userId, partnerId);
        } catch (IllegalStateException e) {
            sendError(session, ErrorCode.TRADE_ALREADY_IN_PROGRESS, e.getMessage()); return;
        }

        Map<String, Object> opened = Map.of("tradeId", trade.tradeId,
            "userIdA", userId, "userIdB", partnerId);
        session.send(router.buildPacket(PacketType.TRADE_OPENED, opened));
        partnerSession.send(router.buildPacket(PacketType.TRADE_OPENED, opened));
    }

    private void handleOfferAdd(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long userId = session.getUserId();
        TradeSession trade = getTrade(session);
        if (trade == null) return;
        long itemId = payload.path("itemId").asLong(-1);
        if (!trade.addItem(userId, itemId)) {
            sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Cannot add item"); return;
        }
        broadcastOffer(trade);
    }

    private void handleOfferRemove(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long userId = session.getUserId();
        TradeSession trade = getTrade(session);
        if (trade == null) return;
        long itemId = payload.path("itemId").asLong(-1);
        trade.removeItem(userId, itemId);
        broadcastOffer(trade);
    }

    private void handleConfirm(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long userId = session.getUserId();
        TradeSession trade = getTrade(session);
        if (trade == null) return;
        trade.confirm(userId);
        broadcastState(trade);
    }

    private void handleAccept(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long userId = session.getUserId();
        TradeSession trade = getTrade(session);
        if (trade == null) return;
        trade.confirm(userId);
        if (trade.isReady()) {
            try {
                tradeService.complete(trade);
                sendToTrade(trade, PacketType.TRADE_COMPLETED,
                    Map.of("tradeId", trade.tradeId));
            } catch (Exception e) {
                log.error("Trade complete failed: {}", trade.tradeId, e);
                tradeService.cancel(trade);
                sendToTrade(trade, PacketType.TRADE_CANCELLED,
                    Map.of("reason", "server_error"));
            }
        } else {
            broadcastState(trade);
        }
    }

    private void handleUnaccept(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        TradeSession trade = getTrade(session);
        if (trade == null) return;
        trade.unconfirm(session.getUserId());
        broadcastState(trade);
    }

    private void handleCancel(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        TradeSession trade = getTrade(session);
        if (trade == null) return;
        tradeService.cancel(trade);
        sendToTrade(trade, PacketType.TRADE_CANCELLED,
            Map.of("tradeId", trade.tradeId, "cancelledBy", session.getUserId()));
    }

    private TradeSession getTrade(WebSocketSession session) {
        var opt = tradeService.getForUser(session.getUserId());
        if (opt.isEmpty()) {
            sendError(session, ErrorCode.TRADE_NOT_IN_PROGRESS, "Not in a trade");
            return null;
        }
        return opt.get();
    }

    private void broadcastOffer(TradeSession trade) {
        sendToTrade(trade, PacketType.TRADE_OFFER_UPDATED, Map.of(
            "tradeId", trade.tradeId,
            "offerA",  trade.getOfferA().stream().toList(),
            "offerB",  trade.getOfferB().stream().toList()
        ));
    }

    private void broadcastState(TradeSession trade) {
        sendToTrade(trade, PacketType.TRADE_CONFIRMED, Map.of(
            "tradeId", trade.tradeId,
            "state",   trade.getState().name()
        ));
    }

    private void sendToTrade(TradeSession trade, String type, Object payload) {
        String json = router.buildPacket(type, payload);
        sessions.byUserId(trade.userIdA).ifPresent(s -> s.send(json));
        sessions.byUserId(trade.userIdB).ifPresent(s -> s.send(json));
    }

    private void sendError(WebSocketSession session, String code, String msg) {
        session.send(router.buildPacket(PacketType.TRADE_ERROR, Map.of("code", code, "message", msg)));
    }
}
