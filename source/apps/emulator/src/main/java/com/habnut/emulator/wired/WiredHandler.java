package com.habnut.emulator.wired;

import com.fasterxml.jackson.databind.JsonNode;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.protocol.ErrorCode;
import com.habnut.emulator.protocol.PacketType;
import com.habnut.emulator.room.Room;
import com.habnut.emulator.room.RoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WiredHandler {

    private static final Logger log = LoggerFactory.getLogger(WiredHandler.class);

    private final WiredEngine engine;
    private final RoomManager roomManager;
    private final PacketRouter router;

    public WiredHandler(WiredEngine engine, RoomManager roomManager, PacketRouter router) {
        this.engine      = engine;
        this.roomManager = roomManager;
        this.router      = router;
    }

    public void register(PacketRouter router) {
        router.register(PacketType.WIRED_TRIGGER_SAVE,   this::handleTriggerSave);
        router.register(PacketType.WIRED_ACTION_SAVE,    this::handleActionSave);
        router.register(PacketType.WIRED_CONDITION_SAVE, this::handleConditionSave);
        router.register(PacketType.WIRED_SELECTOR_SAVE,  this::handleSelectorSave);
        router.register(PacketType.WIRED_VARIABLE_GET,   this::handleVarGet);
        router.register(PacketType.WIRED_VARIABLE_SET,   this::handleVarSet);
        router.register(PacketType.WIRED_VARIABLE_LIST,  this::handleVarList);
        router.register(PacketType.WIRED_DEBUG_START,    this::handleDebugStart);
        router.register(PacketType.WIRED_DEBUG_STOP,     this::handleDebugStop);
        router.register(PacketType.WIRED_SIGNAL_SEND,    this::handleSignalSend);
    }

    private void handleTriggerSave(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long stackId = payload.path("stackId").asLong(-1);
        long furniId = payload.path("furniId").asLong(-1);
        String code  = payload.path("code").asText("");
        if (stackId < 0 || furniId < 0 || code.isBlank()) {
            sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid trigger"); return;
        }
        Room room = getRoomForUser(session);
        if (room == null) return;
        if (!room.hasRight(session.getUserId())) {
            sendError(session, ErrorCode.WIRED_VARIABLE_NOT_FOUND, "No room rights"); return;
        }
        List<WiredStack> stacks = engine.getOrCreateStacks(room.getId());
        WiredStack stack = findOrCreateStack(stacks, stackId, room.getId());
        Map<String, Object> params = parseParams(payload.path("params"));
        stack.setTrigger(new WiredStack.WiredComponent(
            furniId, WiredStack.ComponentType.TRIGGER, code, params));
        saveStack(session, room, stack);
        session.send(router.buildPacket(PacketType.WIRED_TRIGGER_SAVED,
            Map.of("stackId", stackId)));
    }

    private void handleActionSave(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long stackId = payload.path("stackId").asLong(-1);
        long furniId = payload.path("furniId").asLong(-1);
        String code  = payload.path("code").asText("");
        if (stackId < 0 || furniId < 0 || code.isBlank()) {
            sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid action"); return;
        }
        Room room = getRoomForUser(session);
        if (room == null) return;
        if (!room.hasRight(session.getUserId())) {
            sendError(session, ErrorCode.GENERIC_PERMISSION_DENIED, "No room rights"); return;
        }
        List<WiredStack> stacks = engine.getOrCreateStacks(room.getId());
        WiredStack stack = findOrCreateStack(stacks, stackId, room.getId());
        Map<String, Object> params = parseParams(payload.path("params"));
        stack.clearActions();
        stack.addAction(new WiredStack.WiredComponent(
            furniId, WiredStack.ComponentType.ACTION, code, params));
        saveStack(session, room, stack);
        session.send(router.buildPacket(PacketType.WIRED_ACTION_SAVED,
            Map.of("stackId", stackId)));
    }

    private void handleConditionSave(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long stackId = payload.path("stackId").asLong(-1);
        long furniId = payload.path("furniId").asLong(-1);
        String code  = payload.path("code").asText("");
        if (stackId < 0 || furniId < 0 || code.isBlank()) {
            sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid condition"); return;
        }
        Room room = getRoomForUser(session);
        if (room == null) return;
        if (!room.hasRight(session.getUserId())) {
            sendError(session, ErrorCode.GENERIC_PERMISSION_DENIED, "No room rights"); return;
        }
        List<WiredStack> stacks = engine.getOrCreateStacks(room.getId());
        WiredStack stack = findOrCreateStack(stacks, stackId, room.getId());
        Map<String, Object> params = parseParams(payload.path("params"));
        stack.addCondition(new WiredStack.WiredComponent(
            furniId, WiredStack.ComponentType.CONDITION, code, params));
        saveStack(session, room, stack);
        session.send(router.buildPacket(PacketType.WIRED_CONDITION_SAVED,
            Map.of("stackId", stackId)));
    }

    private void handleSelectorSave(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long stackId = payload.path("stackId").asLong(-1);
        long furniId = payload.path("furniId").asLong(-1);
        String code  = payload.path("code").asText("");
        if (stackId < 0 || furniId < 0 || code.isBlank()) {
            sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid selector"); return;
        }
        Room room = getRoomForUser(session);
        if (room == null) return;
        if (!room.hasRight(session.getUserId())) {
            sendError(session, ErrorCode.GENERIC_PERMISSION_DENIED, "No room rights"); return;
        }
        List<WiredStack> stacks = engine.getOrCreateStacks(room.getId());
        WiredStack stack = findOrCreateStack(stacks, stackId, room.getId());
        Map<String, Object> params = parseParams(payload.path("params"));
        stack.addSelector(new WiredStack.WiredComponent(
            furniId, WiredStack.ComponentType.SELECTOR, code, params));
        saveStack(session, room, stack);
        session.send(router.buildPacket(PacketType.WIRED_SELECTOR_SAVED,
            Map.of("stackId", stackId)));
    }

    private void handleVarGet(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        Room room = getRoomForUser(session);
        if (room == null) return;
        String scope = payload.path("scope").asText("room");
        String name  = payload.path("name").asText("");
        WiredContext.Scope s = parseScope(scope);
        WiredValue val = engine.getVariables().get(s, room.getId(), session.getUserId(), name);
        session.send(router.buildPacket(PacketType.WIRED_VARIABLE_RESULT,
            Map.of("scope", scope, "name", name, "value", val.asText(),
                "type", val.getType().name().toLowerCase())));
    }

    private void handleVarSet(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        Room room = getRoomForUser(session);
        if (room == null || !room.hasRight(session.getUserId())) {
            sendError(session, ErrorCode.GENERIC_PERMISSION_DENIED, "No rights"); return;
        }
        String scope = payload.path("scope").asText("room");
        String name  = payload.path("name").asText("");
        String type  = payload.path("type").asText("text");
        JsonNode rawVal = payload.path("value");
        WiredValue value = switch (type) {
            case "number" -> WiredValue.ofNumber(rawVal.asDouble());
            case "bool"   -> WiredValue.ofBool(rawVal.asBoolean());
            default       -> WiredValue.ofText(rawVal.asText());
        };
        engine.getVariables().set(parseScope(scope), room.getId(), session.getUserId(), name, value);
        room.broadcast(PacketType.WIRED_VARIABLE_CHANGED,
            Map.of("scope", scope, "name", name, "value", value.asText()));
    }

    private void handleVarList(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        // Return empty list; full variable listing requires a query against habnut_wired_variables
        session.send(router.buildPacket(PacketType.WIRED_VARIABLE_LIST_RESULT,
            Map.of("variables", List.of())));
    }

    private void handleDebugStart(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        Room room = getRoomForUser(session);
        if (room == null || !room.hasRight(session.getUserId())) return;
        engine.getDebugger().startWatch(room.getId(), session.getUserId());
    }

    private void handleDebugStop(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        Room room = getRoomForUser(session);
        if (room != null) engine.getDebugger().stopWatch(room.getId(), session.getUserId());
    }

    private void handleSignalSend(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        String channel = payload.path("channel").asText("");
        String value   = payload.path("value").asText("");
        boolean global = payload.path("global").asBoolean(false);
        Room room = getRoomForUser(session);
        if (room == null) return;
        WiredValue sig = WiredValue.ofText(value);
        if (global) {
            engine.getSignalBus().emitGlobal(channel, sig);
        } else {
            engine.getSignalBus().emit(room.getId(), channel, sig);
        }
    }

    private Room getRoomForUser(WebSocketSession session) {
        // We need the current room for the user — use room manager
        return roomManager.getRoomForUser(session.getUserId()).orElse(null);
    }

    private WiredStack findOrCreateStack(List<WiredStack> stacks, long stackId, long roomId) {
        for (WiredStack s : stacks) if (s.getStackId() == stackId) return s;
        WiredStack stack = new WiredStack(stackId, roomId);
        stacks.add(stack);
        return stack;
    }

    private void saveStack(WebSocketSession session, Room room, WiredStack stack) {
        try {
            engine.saveStack(room.getId(), stack);
        } catch (SQLException e) {
            log.error("Save wired stack failed: stackId={}", stack.getStackId(), e);
            sendError(session, ErrorCode.GENERIC_INTERNAL_ERROR, "Save failed");
        }
    }

    private Map<String, Object> parseParams(JsonNode node) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (node == null || node.isNull() || !node.isObject()) return params;
        node.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            if (v.isNumber())  params.put(e.getKey(), v.doubleValue());
            else if (v.isBoolean()) params.put(e.getKey(), v.booleanValue());
            else if (v.isArray()) {
                List<Long> ids = new ArrayList<>();
                v.forEach(item -> ids.add(item.asLong()));
                params.put(e.getKey(), ids);
            } else params.put(e.getKey(), v.asText());
        });
        return params;
    }

    private WiredContext.Scope parseScope(String s) {
        return switch (s == null ? "" : s.toLowerCase()) {
            case "user"   -> WiredContext.Scope.USER;
            case "global" -> WiredContext.Scope.GLOBAL;
            default       -> WiredContext.Scope.ROOM;
        };
    }

    private void sendError(WebSocketSession session, String code, String msg) {
        session.send(router.buildPacket(PacketType.WIRED_ERROR, Map.of("code", code, "message", msg)));
    }
}
