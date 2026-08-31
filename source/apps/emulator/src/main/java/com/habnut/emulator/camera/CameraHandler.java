package com.habnut.emulator.camera;

import com.fasterxml.jackson.databind.JsonNode;
import com.habnut.emulator.economy.TransactionService;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.protocol.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public final class CameraHandler {

    private static final Logger log = LoggerFactory.getLogger(CameraHandler.class);

    private final CameraService      cameraService;
    private final TransactionService transactions;
    private final PacketRouter       router;

    public CameraHandler(CameraService cameraService, TransactionService transactions, PacketRouter router) {
        this.cameraService = cameraService;
        this.transactions  = transactions;
        this.router        = router;
    }

    public void register(PacketRouter router) {
        router.register(PacketType.CAM_TAKE,     this::handleTake);
        router.register(PacketType.CAM_PURCHASE, this::handlePurchase);
        router.register(PacketType.CAM_LIST,     this::handleList);
        router.register(PacketType.CAM_DELETE,   this::handleDelete);
    }

    private void handleTake(WebSocketSession session, JsonNode p) {
        long   roomId      = p.path("roomId").asLong(-1);
        String previewData = p.path("preview").asText("");
        long   userId      = session.getUserId();

        try {
            Optional<CameraService.Photo> opt = cameraService.takePhoto(userId, roomId, previewData);
            if (opt.isEmpty()) {
                sendError(session, "photo_limit_reached");
                return;
            }
            CameraService.Photo photo = opt.get();
            session.send(router.buildPacket(PacketType.CAM_RESULT, buildPhotoPayload(photo)));
        } catch (SQLException e) {
            log.error("Camera take error", e);
            sendError(session, "server_error");
        }
    }

    private void handlePurchase(WebSocketSession session, JsonNode p) {
        long photoId = p.path("photoId").asLong(-1);
        long userId  = session.getUserId();

        try {
            // Debit diamonds first
            String idempotencyKey = "cam_purchase:" + userId + ":" + photoId;
            try {
                transactions.debit(userId, TransactionService.Currency.DIAMONDS,
                    cameraService.getPurchaseCostDiamonds(), "camera_purchase", idempotencyKey);
            } catch (IllegalStateException e) {
                sendError(session, "insufficient_diamonds");
                return;
            }

            CameraService.PhotoResult result = cameraService.purchasePhoto(photoId, userId);
            if (result != CameraService.PhotoResult.OK) {
                sendError(session, result.name().toLowerCase());
                return;
            }
            session.send(router.buildPacket(PacketType.CAM_RESULT,
                Map.of("photoId", photoId, "purchased", true)));
        } catch (SQLException e) {
            log.error("Camera purchase error", e);
            sendError(session, "server_error");
        }
    }

    private void handleList(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        try {
            List<CameraService.Photo> photos = cameraService.listPhotos(userId);
            session.send(router.buildPacket(PacketType.CAM_PHOTO_LIST_RESULT, Map.of(
                "photos", photos.stream().map(this::buildPhotoPayload).collect(Collectors.toList()))));
        } catch (SQLException e) {
            log.error("Camera list error", e);
            sendError(session, "server_error");
        }
    }

    private void handleDelete(WebSocketSession session, JsonNode p) {
        long photoId = p.path("photoId").asLong(-1);
        long userId  = session.getUserId();
        try {
            boolean ok = cameraService.deletePhoto(photoId, userId);
            if (ok) {
                session.send(router.buildPacket(PacketType.CAM_PHOTO_DELETED, Map.of("photoId", photoId)));
            } else {
                sendError(session, "not_found");
            }
        } catch (SQLException e) {
            log.error("Camera delete error", e);
            sendError(session, "server_error");
        }
    }

    private Map<String, Object> buildPhotoPayload(CameraService.Photo photo) {
        return Map.of("id", photo.id(), "roomId", photo.roomId(),
            "token", photo.imageToken(), "purchased", photo.purchased(),
            "takenAt", photo.takenAt());
    }

    private void sendError(WebSocketSession session, String reason) {
        session.send(router.buildPacket("camera.error", Map.of("reason", reason)));
    }
}
