package com.habnut.emulator.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.habnut.emulator.protocol.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class PacketRouter {

    private static final Logger log = LoggerFactory.getLogger(PacketRouter.class);

    public interface Handler {
        void handle(WebSocketSession session, JsonNode payload) throws Exception;
    }

    private final ObjectMapper mapper;
    private final Map<String, Handler> handlers = new HashMap<>();

    public PacketRouter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void register(String type, Handler handler) {
        handlers.put(type, handler);
    }

    public void dispatch(WebSocketSession session, String raw) {
        JsonNode root;
        try {
            root = mapper.readTree(raw);
        } catch (Exception e) {
            sendError(session, null, ErrorCode.GENERIC_INVALID_PAYLOAD, "Malformed JSON");
            return;
        }

        String type = root.path("type").asText(null);
        String id   = root.path("id").asText(UUID.randomUUID().toString());
        JsonNode payload = root.path("payload");

        if (type == null || type.isBlank()) {
            sendError(session, id, ErrorCode.GENERIC_INVALID_PAYLOAD, "Missing packet type");
            return;
        }

        Handler handler = handlers.get(type);
        if (handler == null) {
            log.warn("No handler for packet type '{}' from session {}", type, session.sessionId);
            sendError(session, id, ErrorCode.GENERIC_INVALID_PAYLOAD, "Unknown packet type: " + type);
            return;
        }

        try {
            handler.handle(session, payload);
        } catch (Exception e) {
            log.error("Error handling packet type '{}' from session {}", type, session.sessionId, e);
            sendError(session, id, ErrorCode.GENERIC_INTERNAL_ERROR, "Internal server error");
        }
    }

    public String buildPacket(String type, Object payload) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("type", type);
            node.put("id", UUID.randomUUID().toString());
            node.set("payload", mapper.valueToTree(payload));
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            log.error("Failed to serialise packet type '{}'", type, e);
            return "{\"type\":\"system.error\",\"payload\":{\"code\":\"GENERIC_SERVER_ERROR\"}}";
        }
    }

    private void sendError(WebSocketSession session, String id, String code, String message) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("type", "system.error");
            node.put("id", id != null ? id : UUID.randomUUID().toString());
            ObjectNode payload = mapper.createObjectNode();
            payload.put("code", code);
            payload.put("message", message);
            node.set("payload", payload);
            session.send(mapper.writeValueAsString(node));
        } catch (Exception ex) {
            log.error("Failed to send error to session {}", session.sessionId, ex);
        }
    }
}
