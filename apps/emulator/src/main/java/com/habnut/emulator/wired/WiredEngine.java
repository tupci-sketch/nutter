package com.habnut.emulator.wired;

import com.habnut.emulator.db.DatabaseManager;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.SessionRegistry;
import com.habnut.emulator.room.Room;
import com.habnut.emulator.room.RoomEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class WiredEngine {

    private static final Logger log = LoggerFactory.getLogger(WiredEngine.class);

    private final WiredRegistry     registry;
    private final WiredVariableStore variables;
    private final WiredSignalBus     signalBus;
    private final WiredDebugger      debugger;
    private final WiredExecutor      executor;
    private final DatabaseManager    db;

    // roomId → list of stacks
    private final ConcurrentHashMap<Long, List<WiredStack>> roomStacks = new ConcurrentHashMap<>();

    public WiredEngine(DatabaseManager db, SessionRegistry sessions, PacketRouter router) {
        this.db        = db;
        this.registry  = new WiredRegistry();
        this.variables = new WiredVariableStore(db);
        this.signalBus = new WiredSignalBus();
        this.debugger  = new WiredDebugger(sessions, router);
        this.executor  = new WiredExecutor(registry, debugger);

        WiredDefinitions.registerTriggers(registry, this);
        WiredDefinitions.registerConditions(registry);
        WiredDefinitions.registerSelectors(registry);
        WiredDefinitions.registerActions(registry);
    }

    // Called by room system when a stack's trigger event fires
    public void fireTrigger(long roomId, Room room, RoomEntity entity,
                            String triggerCode, Map<String, Object> locals) {
        List<WiredStack> stacks = roomStacks.get(roomId);
        if (stacks == null || stacks.isEmpty()) return;

        for (WiredStack stack : stacks) {
            if (stack.getTrigger() == null) continue;
            if (!triggerCode.equals(stack.getTrigger().definitionCode())) continue;

            WiredContext ctx = new WiredContext(roomId, room, entity, variables, signalBus);
            ctx.locals.putAll(locals);
            try {
                boolean triggered = executor.execute(stack, ctx);
                debugger.logExecution(roomId, stack.getStackId(), triggered);
            } catch (WiredExecutionLimitException e) {
                log.warn("Wired execution limit: roomId={} stackId={}", roomId, stack.getStackId());
            } catch (Exception e) {
                log.error("Wired execution error: roomId={} stackId={}", roomId, stack.getStackId(), e);
            }
        }
    }

    public void loadRoom(long roomId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT wi.id as stack_id, wi.furni_id, wi.component_type, " +
                 "wi.definition_code, wi.params_json " +
                 "FROM habnut_wired_items wi " +
                 "WHERE wi.room_id = ? ORDER BY wi.component_type, wi.stack_order")) {
            ps.setLong(1, roomId);
            Map<Long, WiredStack> stackMap = new LinkedHashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long stackId   = rs.getLong("stack_id");
                    long furniId   = rs.getLong("furni_id");
                    String typeStr = rs.getString("component_type");
                    String code    = rs.getString("definition_code");
                    String paramsJson = rs.getString("params_json");

                    WiredStack stack = stackMap.computeIfAbsent(stackId,
                        k -> new WiredStack(k, roomId));
                    Map<String, Object> params = parseParams(paramsJson);
                    WiredStack.ComponentType ct = WiredStack.ComponentType.valueOf(typeStr.toUpperCase());
                    WiredStack.WiredComponent component =
                        new WiredStack.WiredComponent(furniId, ct, code, params);

                    switch (ct) {
                        case TRIGGER   -> stack.setTrigger(component);
                        case CONDITION -> stack.addCondition(component);
                        case SELECTOR  -> stack.addSelector(component);
                        case ACTION    -> stack.addAction(component);
                    }
                }
            }
            roomStacks.put(roomId, new ArrayList<>(stackMap.values()));
            signalBus.subscribe(roomId, (rid, channel, payload) ->
                fireTrigger(rid, null, null, "trigger.signal_received",
                    Map.of("signalChannel", channel, "signalPayload", payload)));
            log.debug("Wired engine loaded {} stacks for room {}", stackMap.size(), roomId);
        } catch (SQLException e) {
            log.error("loadRoom wired failed: roomId={}", roomId, e);
        }
    }

    public void unloadRoom(long roomId) {
        roomStacks.remove(roomId);
        signalBus.unsubscribeAll(roomId);
        variables.evictRoom(roomId);
    }

    public void saveStack(long roomId, WiredStack stack) throws SQLException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM habnut_wired_items WHERE id = ?")) {
                del.setLong(1, stack.getStackId()); del.executeUpdate();
            }
            int order = 0;
            List<WiredStack.WiredComponent> all = new ArrayList<>();
            if (stack.getTrigger() != null) all.add(stack.getTrigger());
            all.addAll(stack.getConditions());
            all.addAll(stack.getSelectors());
            all.addAll(stack.getActions());
            for (WiredStack.WiredComponent c : all) {
                try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO habnut_wired_items " +
                    "(id, room_id, furni_id, component_type, definition_code, params_json, stack_order) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ins.setLong(1, stack.getStackId()); ins.setLong(2, roomId);
                    ins.setLong(3, c.furniId());
                    ins.setString(4, c.componentType().name().toLowerCase());
                    ins.setString(5, c.definitionCode());
                    ins.setString(6, serializeParams(c.params()));
                    ins.setInt(7, order++);
                    ins.executeUpdate();
                }
            }
            conn.commit();
        }
    }

    public WiredDebugger getDebugger()       { return debugger; }
    public WiredVariableStore getVariables() { return variables; }
    public WiredSignalBus getSignalBus()     { return signalBus; }
    public WiredRegistry getRegistry()       { return registry; }

    public List<WiredStack> getOrCreateStacks(long roomId) {
        return roomStacks.computeIfAbsent(roomId, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParams(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) return Map.of();
        try {
            // Simple JSON object parser — no external dependency needed for flat string/number maps
            Map<String, Object> result = new LinkedHashMap<>();
            json = json.trim();
            if (json.startsWith("{")) json = json.substring(1);
            if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
            // Split on top-level commas (simplified — handles flat objects only)
            for (String pair : splitTopLevel(json)) {
                int colon = pair.indexOf(':');
                if (colon < 0) continue;
                String key = pair.substring(0, colon).trim().replaceAll("^\"|\"$", "");
                String val = pair.substring(colon + 1).trim();
                if (val.startsWith("\"")) {
                    result.put(key, val.replaceAll("^\"|\"$", ""));
                } else if ("true".equals(val)) {
                    result.put(key, true);
                } else if ("false".equals(val)) {
                    result.put(key, false);
                } else {
                    try { result.put(key, Double.parseDouble(val)); }
                    catch (NumberFormatException e) { result.put(key, val); }
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse wired params: {}", json);
            return Map.of();
        }
    }

    private List<String> splitTopLevel(String s) {
        List<String> result = new ArrayList<>();
        int depth = 0; int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') depth--;
            else if (c == ',' && depth == 0) {
                result.add(s.substring(start, i)); start = i + 1;
            }
        }
        if (start < s.length()) result.add(s.substring(start));
        return result;
    }

    private String serializeParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (!first) sb.append(',');
            sb.append('"').append(e.getKey()).append('"').append(':');
            Object v = e.getValue();
            if (v instanceof String s) sb.append('"').append(s).append('"');
            else if (v instanceof Boolean b) sb.append(b);
            else sb.append(v);
            first = false;
        }
        return sb.append('}').toString();
    }
}
