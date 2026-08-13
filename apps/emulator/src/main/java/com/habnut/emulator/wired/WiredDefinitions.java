package com.habnut.emulator.wired;

import com.habnut.emulator.room.Room;
import com.habnut.emulator.room.RoomEntity;

import java.util.*;

public final class WiredDefinitions {

    private WiredDefinitions() {}

    // --------------- TRIGGERS (21) ---------------

    public static void registerTriggers(WiredRegistry r, WiredEngine engine) {
        // Player movement triggers
        r.registerTrigger("trigger.walk_on_furni", (stack, ctx) ->
            ctx.locals.containsKey("walkedOnFurni") &&
            matchesFurni(stack, ctx, "walkedOnFurni"));
        r.registerTrigger("trigger.walk_off_furni", (stack, ctx) ->
            ctx.locals.containsKey("walkedOffFurni") &&
            matchesFurni(stack, ctx, "walkedOffFurni"));
        r.registerTrigger("trigger.enter_room", (stack, ctx) ->
            "enter_room".equals(ctx.locals.get("triggerEvent")));
        r.registerTrigger("trigger.leave_room", (stack, ctx) ->
            "leave_room".equals(ctx.locals.get("triggerEvent")));
        r.registerTrigger("trigger.say", (stack, ctx) -> {
            String said = (String) ctx.locals.get("chatMessage");
            String match = paramStr(stack.getTrigger(), "message");
            if (said == null) return false;
            return match.isBlank() || said.equalsIgnoreCase(match);
        });
        r.registerTrigger("trigger.say_contains", (stack, ctx) -> {
            String said = (String) ctx.locals.get("chatMessage");
            String match = paramStr(stack.getTrigger(), "message");
            return said != null && !match.isBlank() && said.toLowerCase().contains(match.toLowerCase());
        });
        r.registerTrigger("trigger.furni_state_changed", (stack, ctx) ->
            ctx.locals.containsKey("furniStateChanged") &&
            matchesFurni(stack, ctx, "furniStateChanged"));
        r.registerTrigger("trigger.score_achieved", (stack, ctx) -> {
            Object score = ctx.locals.get("gameScore");
            if (score == null) return false;
            int required = paramInt(stack.getTrigger(), "score");
            return ((Number) score).intValue() >= required;
        });
        r.registerTrigger("trigger.timer", (stack, ctx) ->
            "timer".equals(ctx.locals.get("triggerEvent")));
        r.registerTrigger("trigger.game_starts", (stack, ctx) ->
            "game_start".equals(ctx.locals.get("triggerEvent")));
        r.registerTrigger("trigger.game_ends", (stack, ctx) ->
            "game_end".equals(ctx.locals.get("triggerEvent")));
        r.registerTrigger("trigger.collision", (stack, ctx) ->
            ctx.locals.containsKey("collision"));
        r.registerTrigger("trigger.avatar_enters_area", (stack, ctx) ->
            matchesArea(stack, ctx, "enteredArea"));
        r.registerTrigger("trigger.avatar_leaves_area", (stack, ctx) ->
            matchesArea(stack, ctx, "leftArea"));
        r.registerTrigger("trigger.toggle_furni", (stack, ctx) ->
            ctx.locals.containsKey("toggledFurni") &&
            matchesFurni(stack, ctx, "toggledFurni"));
        r.registerTrigger("trigger.bot_reaches_furni", (stack, ctx) ->
            ctx.locals.containsKey("botReachedFurni"));
        r.registerTrigger("trigger.bot_reached_avat", (stack, ctx) ->
            ctx.locals.containsKey("botReachedAvatar"));
        r.registerTrigger("trigger.signal_received", (stack, ctx) -> {
            String channel = (String) ctx.locals.get("signalChannel");
            String expected = paramStr(stack.getTrigger(), "channel");
            return channel != null && channel.equals(expected);
        });
        r.registerTrigger("trigger.variable_changed", (stack, ctx) -> {
            String varName = (String) ctx.locals.get("changedVarName");
            return varName != null && varName.equals(paramStr(stack.getTrigger(), "varName"));
        });
        r.registerTrigger("trigger.periodic_long", (stack, ctx) ->
            "periodic_long".equals(ctx.locals.get("triggerEvent")));
        r.registerTrigger("trigger.periodic_short", (stack, ctx) ->
            "periodic_short".equals(ctx.locals.get("triggerEvent")));
    }

    // --------------- CONDITIONS (37) ---------------

    public static void registerConditions(WiredRegistry r) {
        // Actor position conditions
        r.registerCondition("cond.actor_in_group", (c, ctx) -> {
            if (ctx.triggeringEntity == null) return false;
            List<?> groupIds = (List<?>) c.params().get("groupIds");
            if (groupIds == null || groupIds.isEmpty()) return true;
            Object userGroup = ctx.locals.get("actorGroupId");
            return groupIds.contains(userGroup);
        });
        r.registerCondition("cond.not_actor_in_group", (c, ctx) -> {
            if (ctx.triggeringEntity == null) return true;
            List<?> groupIds = (List<?>) c.params().get("groupIds");
            if (groupIds == null || groupIds.isEmpty()) return true;
            Object userGroup = ctx.locals.get("actorGroupId");
            return !groupIds.contains(userGroup);
        });
        r.registerCondition("cond.furni_has_users_on", (c, ctx) -> {
            Object count = ctx.locals.get("furniUserCount");
            return count instanceof Number n && n.intValue() > 0;
        });
        r.registerCondition("cond.furni_has_no_users", (c, ctx) -> {
            Object count = ctx.locals.get("furniUserCount");
            return count == null || (count instanceof Number n && n.intValue() == 0);
        });
        r.registerCondition("cond.actor_on_furni", (c, ctx) ->
            ctx.locals.containsKey("actorOnFurni"));
        r.registerCondition("cond.not_actor_on_furni", (c, ctx) ->
            !ctx.locals.containsKey("actorOnFurni"));
        r.registerCondition("cond.furni_state_is", (c, ctx) -> {
            Object state = ctx.locals.get("furniState");
            int expected = paramInt(c, "state");
            return state instanceof Number n && n.intValue() == expected;
        });
        r.registerCondition("cond.furni_state_is_not", (c, ctx) -> {
            Object state = ctx.locals.get("furniState");
            int expected = paramInt(c, "state");
            return !(state instanceof Number n) || n.intValue() != expected;
        });
        // Variable comparison conditions (covers most numeric/text/bool comparisons)
        r.registerCondition("cond.var_eq", (c, ctx) ->
            resolveVar(c, ctx).eq(resolveValue(c, ctx)));
        r.registerCondition("cond.var_not_eq", (c, ctx) ->
            !resolveVar(c, ctx).eq(resolveValue(c, ctx)));
        r.registerCondition("cond.var_gt", (c, ctx) ->
            resolveVar(c, ctx).gt(resolveValue(c, ctx)));
        r.registerCondition("cond.var_lt", (c, ctx) ->
            resolveVar(c, ctx).lt(resolveValue(c, ctx)));
        r.registerCondition("cond.var_gte", (c, ctx) ->
            resolveVar(c, ctx).gte(resolveValue(c, ctx)));
        r.registerCondition("cond.var_lte", (c, ctx) ->
            resolveVar(c, ctx).lte(resolveValue(c, ctx)));
        r.registerCondition("cond.var_contains", (c, ctx) ->
            resolveVar(c, ctx).contains(resolveValue(c, ctx)).asBool());
        // Time conditions
        r.registerCondition("cond.date_range_active", (c, ctx) -> {
            long now = System.currentTimeMillis();
            long from = paramLong(c, "from"); long to = paramLong(c, "to");
            return now >= from && now <= to;
        });
        // User conditions
        r.registerCondition("cond.actor_is_room_owner", (c, ctx) ->
            ctx.triggeringEntity != null &&
            ctx.triggeringEntity.getUserId() == ctx.room.getOwnerId());
        r.registerCondition("cond.actor_has_room_rights", (c, ctx) ->
            ctx.triggeringEntity != null &&
            ctx.room.hasRight(ctx.triggeringEntity.getUserId()));
        r.registerCondition("cond.not_actor_has_room_rights", (c, ctx) ->
            ctx.triggeringEntity == null ||
            !ctx.room.hasRight(ctx.triggeringEntity.getUserId()));
        r.registerCondition("cond.team_wins", (c, ctx) -> {
            Object team = ctx.locals.get("winningTeam");
            String expected = paramStr(c, "team");
            return team != null && team.toString().equals(expected);
        });
        r.registerCondition("cond.not_team_wins", (c, ctx) -> {
            Object team = ctx.locals.get("winningTeam");
            String expected = paramStr(c, "team");
            return team == null || !team.toString().equals(expected);
        });
        r.registerCondition("cond.score_gte", (c, ctx) -> {
            Object score = ctx.locals.get("gameScore");
            int required = paramInt(c, "score");
            return score instanceof Number n && n.intValue() >= required;
        });
        r.registerCondition("cond.score_lt", (c, ctx) -> {
            Object score = ctx.locals.get("gameScore");
            int required = paramInt(c, "score");
            return score instanceof Number n && n.intValue() < required;
        });
        r.registerCondition("cond.trigger_on_furni", (c, ctx) ->
            ctx.locals.containsKey("triggerFurniId") &&
            matchesFurni(c, ctx, "triggerFurniId"));
        r.registerCondition("cond.not_trigger_on_furni", (c, ctx) ->
            !ctx.locals.containsKey("triggerFurniId") ||
            !matchesFurni(c, ctx, "triggerFurniId"));
        r.registerCondition("cond.actor_in_team", (c, ctx) -> {
            Object team = ctx.locals.get("actorTeam");
            return team != null && team.toString().equals(paramStr(c, "team"));
        });
        r.registerCondition("cond.not_actor_in_team", (c, ctx) -> {
            Object team = ctx.locals.get("actorTeam");
            return team == null || !team.toString().equals(paramStr(c, "team"));
        });
        r.registerCondition("cond.user_count_gte", (c, ctx) -> {
            int count = ctx.room.getEntityCount();
            return count >= paramInt(c, "count");
        });
        r.registerCondition("cond.user_count_lt", (c, ctx) -> {
            int count = ctx.room.getEntityCount();
            return count < paramInt(c, "count");
        });
        r.registerCondition("cond.signal_value_eq", (c, ctx) -> {
            WiredValue sig = (WiredValue) ctx.locals.get("signalPayload");
            return sig != null && sig.eq(WiredValue.ofText(paramStr(c, "value")));
        });
        r.registerCondition("cond.var_is_type", (c, ctx) -> {
            WiredValue v = resolveVar(c, ctx);
            String expected = paramStr(c, "type");
            return v.getType().name().equalsIgnoreCase(expected);
        });
        r.registerCondition("cond.always_true",  (c, ctx) -> true);
        r.registerCondition("cond.always_false", (c, ctx) -> false);
        r.registerCondition("cond.random_chance", (c, ctx) -> {
            int percent = Math.max(0, Math.min(100, paramInt(c, "percent")));
            return Math.random() * 100 < percent;
        });
        r.registerCondition("cond.is_day",   (c, ctx) -> isHourBetween(6, 18));
        r.registerCondition("cond.is_night",  (c, ctx) -> !isHourBetween(6, 18));
    }

    // --------------- SELECTORS (22) ---------------

    public static void registerSelectors(WiredRegistry r) {
        r.registerSelector("sel.random_furni", (c, ctx) -> {
            List<Long> all = furniListParam(c);
            if (all.isEmpty()) return List.of();
            return List.of(all.get((int)(Math.random() * all.size())));
        });
        r.registerSelector("sel.all_furni",       (c, ctx) -> furniListParam(c));
        r.registerSelector("sel.specific_furnis", (c, ctx) -> furniListParam(c));
        r.registerSelector("sel.furni_in_room",   (c, ctx) -> ctx.room.getAllFurniIds());
        r.registerSelector("sel.users_on_furni",  (c, ctx) -> ctx.room.getUsersOnFurni(
            paramLong(c, "furniId")));
        r.registerSelector("sel.users_in_area",   (c, ctx) -> {
            int x1 = paramInt(c, "x1"); int y1 = paramInt(c, "y1");
            int x2 = paramInt(c, "x2"); int y2 = paramInt(c, "y2");
            return ctx.room.getUsersInArea(x1, y1, x2, y2);
        });
        r.registerSelector("sel.all_users",       (c, ctx) -> ctx.room.getAllUserIds());
        r.registerSelector("sel.actor",           (c, ctx) ->
            ctx.triggeringEntity == null ? List.of() : List.of(ctx.triggeringEntity.getUserId()));
        r.registerSelector("sel.furni_by_type",   (c, ctx) -> ctx.room.getFurniByType(paramStr(c, "type")));
        r.registerSelector("sel.furni_with_state",(c, ctx) -> ctx.room.getFurniWithState(paramInt(c, "state")));
        r.registerSelector("sel.closest_furni",   (c, ctx) -> {
            if (ctx.triggeringEntity == null) return List.of();
            return ctx.room.getClosestFurni(ctx.triggeringEntity.getPosition(), furniListParam(c));
        });
        r.registerSelector("sel.first_furni",     (c, ctx) -> {
            List<Long> l = furniListParam(c); return l.isEmpty() ? List.of() : List.of(l.get(0));
        });
        r.registerSelector("sel.last_furni",      (c, ctx) -> {
            List<Long> l = furniListParam(c); return l.isEmpty() ? List.of() : List.of(l.get(l.size()-1));
        });
        r.registerSelector("sel.nth_furni", (c, ctx) -> {
            List<Long> l = furniListParam(c); int n = paramInt(c, "n");
            return n >= 0 && n < l.size() ? List.of(l.get(n)) : List.of();
        });
        r.registerSelector("sel.trigger_furni", (c, ctx) -> {
            Object id = ctx.locals.get("triggerFurniId");
            return id instanceof Long lid ? List.of(lid) : List.of();
        });
        r.registerSelector("sel.users_in_team", (c, ctx) ->
            ctx.room.getUsersInTeam(paramStr(c, "team")));
        r.registerSelector("sel.users_with_badge", (c, ctx) ->
            ctx.room.getUsersWithBadge(paramStr(c, "badge")));
        r.registerSelector("sel.bots_in_room",  (c, ctx) -> ctx.room.getBotIds());
        r.registerSelector("sel.selected",      (c, ctx) -> {
            Object sel = ctx.locals.get("selectedFurnis");
            return sel instanceof List<?> l ? l.stream().map(o -> (Long) o).toList() : List.of();
        });
        r.registerSelector("sel.random_users",  (c, ctx) -> {
            List<Long> all = ctx.room.getAllUserIds();
            int count = Math.min(paramInt(c, "count"), all.size());
            List<Long> copy = new ArrayList<>(all);
            Collections.shuffle(copy);
            return copy.subList(0, count);
        });
        r.registerSelector("sel.not_actor",     (c, ctx) -> {
            long actorId = ctx.triggeringEntity != null ? ctx.triggeringEntity.getUserId() : -1;
            return ctx.room.getAllUserIds().stream().filter(id -> id != actorId).toList();
        });
        r.registerSelector("sel.empty", (c, ctx) -> List.of());
    }

    // --------------- ACTIONS (55) ---------------

    public static void registerActions(WiredRegistry r) {
        // Movement
        r.registerAction("act.move_furni", (c, ctx) -> {
            int dx = paramInt(c, "dx"); int dy = paramInt(c, "dy");
            selectedFurniIds(ctx).forEach(id -> ctx.room.moveFurni(id, dx, dy));
        });
        r.registerAction("act.set_furni_state", (c, ctx) -> {
            int state = paramInt(c, "state");
            selectedFurniIds(ctx).forEach(id -> ctx.room.setFurniState(id, state));
        });
        r.registerAction("act.toggle_furni_state", (c, ctx) ->
            selectedFurniIds(ctx).forEach(id -> ctx.room.toggleFurniState(id)));
        r.registerAction("act.move_avatar_to", (c, ctx) -> {
            int x = paramInt(c, "x"); int y = paramInt(c, "y");
            selectedUserIds(ctx).forEach(uid -> ctx.room.teleportUser(uid, x, y));
        });
        r.registerAction("act.teleport_user", (c, ctx) -> {
            int x = paramInt(c, "x"); int y = paramInt(c, "y");
            selectedUserIds(ctx).forEach(uid -> ctx.room.teleportUser(uid, x, y));
        });
        r.registerAction("act.kick_user", (c, ctx) ->
            selectedUserIds(ctx).forEach(uid -> ctx.room.kickUser(uid, "wired")));
        r.registerAction("act.give_badge", (c, ctx) -> {
            String badge = paramStr(c, "badge");
            selectedUserIds(ctx).forEach(uid -> ctx.locals.put("grantBadge:" + uid, badge));
        });
        r.registerAction("act.give_credits", (c, ctx) -> {
            int amount = paramInt(c, "amount");
            selectedUserIds(ctx).forEach(uid -> ctx.locals.put("grantCredits:" + uid, amount));
        });
        r.registerAction("act.give_nut_points", (c, ctx) -> {
            int amount = paramInt(c, "amount");
            selectedUserIds(ctx).forEach(uid -> ctx.locals.put("grantNutPoints:" + uid, amount));
        });
        r.registerAction("act.chat", (c, ctx) -> {
            String msg = paramStr(c, "message");
            ctx.room.broadcastWiredChat(msg);
        });
        r.registerAction("act.chat_to_user", (c, ctx) -> {
            String msg = paramStr(c, "message");
            selectedUserIds(ctx).forEach(uid -> ctx.room.whisperUser(uid, msg));
        });
        r.registerAction("act.set_room_owner_effect", (c, ctx) ->
            ctx.room.setOwnerEffect(paramInt(c, "effectId")));
        r.registerAction("act.reset_furni", (c, ctx) ->
            selectedFurniIds(ctx).forEach(id -> ctx.room.resetFurniState(id)));
        r.registerAction("act.rotate_furni", (c, ctx) -> {
            int rotation = paramInt(c, "rotation");
            selectedFurniIds(ctx).forEach(id -> ctx.room.rotateFurni(id, rotation));
        });
        r.registerAction("act.furni_to_user", (c, ctx) -> {
            if (ctx.triggeringEntity == null) return;
            selectedFurniIds(ctx).forEach(id ->
                ctx.room.moveFurniTo(id, ctx.triggeringEntity.getPosition()));
        });
        r.registerAction("act.user_to_furni", (c, ctx) -> {
            long furniId = paramLong(c, "furniId");
            ctx.room.getFurniPosition(furniId).ifPresent(pos ->
                selectedUserIds(ctx).forEach(uid -> ctx.room.teleportUser(uid,
                    pos[0], pos[1])));
        });
        r.registerAction("act.give_score", (c, ctx) -> {
            int amount = paramInt(c, "amount");
            ctx.locals.merge("gameScore", amount, (a, b) -> (int) a + (int) b);
        });
        r.registerAction("act.add_team_score", (c, ctx) -> {
            String team = paramStr(c, "team"); int amount = paramInt(c, "amount");
            ctx.locals.put("teamScore:" + team,
                ((Number) ctx.locals.getOrDefault("teamScore:" + team, 0)).intValue() + amount);
        });
        // Variable operations
        r.registerAction("act.set_var", (c, ctx) -> {
            WiredContext.Scope scope = parseScope(paramStr(c, "scope"));
            String name = paramStr(c, "varName");
            ctx.setVariable(scope, name, resolveValue(c, ctx));
        });
        r.registerAction("act.add_var", (c, ctx) -> {
            WiredContext.Scope scope = parseScope(paramStr(c, "scope"));
            String name = paramStr(c, "varName");
            WiredValue current = ctx.getVariable(scope, name);
            ctx.setVariable(scope, name, current.add(resolveValue(c, ctx)));
        });
        r.registerAction("act.sub_var", (c, ctx) -> {
            WiredContext.Scope scope = parseScope(paramStr(c, "scope"));
            String name = paramStr(c, "varName");
            WiredValue current = ctx.getVariable(scope, name);
            ctx.setVariable(scope, name, current.sub(resolveValue(c, ctx)));
        });
        r.registerAction("act.mul_var", (c, ctx) -> {
            WiredContext.Scope scope = parseScope(paramStr(c, "scope"));
            String name = paramStr(c, "varName");
            WiredValue current = ctx.getVariable(scope, name);
            ctx.setVariable(scope, name, current.mul(resolveValue(c, ctx)));
        });
        r.registerAction("act.div_var", (c, ctx) -> {
            WiredContext.Scope scope = parseScope(paramStr(c, "scope"));
            String name = paramStr(c, "varName");
            WiredValue current = ctx.getVariable(scope, name);
            ctx.setVariable(scope, name, current.div(resolveValue(c, ctx)));
        });
        r.registerAction("act.mod_var", (c, ctx) -> {
            WiredContext.Scope scope = parseScope(paramStr(c, "scope"));
            String name = paramStr(c, "varName");
            WiredValue current = ctx.getVariable(scope, name);
            ctx.setVariable(scope, name, current.mod(resolveValue(c, ctx)));
        });
        r.registerAction("act.reset_var", (c, ctx) -> {
            WiredContext.Scope scope = parseScope(paramStr(c, "scope"));
            ctx.setVariable(scope, paramStr(c, "varName"), WiredValue.ZERO);
        });
        r.registerAction("act.var_to_chat", (c, ctx) -> {
            WiredContext.Scope scope = parseScope(paramStr(c, "scope"));
            WiredValue v = ctx.getVariable(scope, paramStr(c, "varName"));
            ctx.room.broadcastWiredChat(v.asText());
        });
        r.registerAction("act.concat_var", (c, ctx) -> {
            WiredContext.Scope scope = parseScope(paramStr(c, "scope"));
            String name = paramStr(c, "varName");
            WiredValue current = ctx.getVariable(scope, name);
            ctx.setVariable(scope, name, current.add(resolveValue(c, ctx)));
        });
        // Signal actions
        r.registerAction("act.emit_signal", (c, ctx) ->
            ctx.emitSignal(paramStr(c, "channel"), resolveValue(c, ctx)));
        r.registerAction("act.emit_global_signal", (c, ctx) ->
            ctx.locals.put("emitGlobalSignal", List.of(paramStr(c, "channel"), resolveValue(c, ctx))));
        // Room state
        r.registerAction("act.set_room_lighting", (c, ctx) ->
            ctx.room.setLighting(paramStr(c, "preset")));
        r.registerAction("act.set_room_music", (c, ctx) ->
            ctx.room.setMusicTrack(paramInt(c, "trackId")));
        r.registerAction("act.show_notification", (c, ctx) -> {
            String msg = paramStr(c, "message");
            selectedUserIds(ctx).forEach(uid -> ctx.room.sendNotification(uid, msg));
        });
        r.registerAction("act.set_floor_item_data", (c, ctx) -> {
            String data = paramStr(c, "data");
            selectedFurniIds(ctx).forEach(id -> ctx.room.setFurniData(id, data));
        });
        r.registerAction("act.toggle_floor_item_sticky_pole", (c, ctx) ->
            selectedFurniIds(ctx).forEach(id -> ctx.room.toggleStickyPole(id)));
        r.registerAction("act.move_and_rotate_furni", (c, ctx) -> {
            int dx = paramInt(c, "dx"); int dy = paramInt(c, "dy");
            int rotation = paramInt(c, "rotation");
            selectedFurniIds(ctx).forEach(id -> {
                ctx.room.moveFurni(id, dx, dy);
                ctx.room.rotateFurni(id, rotation);
            });
        });
        r.registerAction("act.set_avatar_effect", (c, ctx) -> {
            int effectId = paramInt(c, "effectId");
            selectedUserIds(ctx).forEach(uid -> ctx.room.setUserEffect(uid, effectId));
        });
        r.registerAction("act.sit_user",   (c, ctx) -> selectedUserIds(ctx).forEach(uid -> ctx.room.sitUser(uid)));
        r.registerAction("act.stand_user", (c, ctx) -> selectedUserIds(ctx).forEach(uid -> ctx.room.standUser(uid)));
        r.registerAction("act.wave_user",  (c, ctx) -> selectedUserIds(ctx).forEach(uid -> ctx.room.waveUser(uid)));
        r.registerAction("act.dance_user", (c, ctx) -> {
            int danceId = paramInt(c, "danceId");
            selectedUserIds(ctx).forEach(uid -> ctx.room.danceUser(uid, danceId));
        });
        r.registerAction("act.start_game",   (c, ctx) -> ctx.locals.put("startGame", true));
        r.registerAction("act.end_game",     (c, ctx) -> ctx.locals.put("endGame", true));
        r.registerAction("act.reset_game",   (c, ctx) -> ctx.locals.put("resetGame", true));
        r.registerAction("act.toggle_game",  (c, ctx) -> ctx.locals.put("toggleGame", true));
        r.registerAction("act.freeze_user",  (c, ctx) ->
            selectedUserIds(ctx).forEach(uid -> ctx.locals.put("freeze:" + uid, true)));
        r.registerAction("act.unfreeze_user",(c, ctx) ->
            selectedUserIds(ctx).forEach(uid -> ctx.locals.remove("freeze:" + uid)));
        r.registerAction("act.move_bot", (c, ctx) -> {
            int x = paramInt(c, "x"); int y = paramInt(c, "y");
            ctx.room.moveBotTo(paramLong(c, "botId"), x, y);
        });
        r.registerAction("act.bot_chat", (c, ctx) ->
            ctx.room.botChat(paramLong(c, "botId"), paramStr(c, "message")));
        r.registerAction("act.open_link", (c, ctx) -> {
            String url = paramStr(c, "url");
            selectedUserIds(ctx).forEach(uid -> ctx.room.sendOpenLink(uid, url));
        });
        r.registerAction("act.set_timer", (c, ctx) ->
            ctx.locals.put("wiredTimer:" + paramStr(c, "timerId"), paramLong(c, "delayMs")));
        r.registerAction("act.cancel_timer", (c, ctx) ->
            ctx.locals.remove("wiredTimer:" + paramStr(c, "timerId")));
        r.registerAction("act.chest_give_item", (c, ctx) ->
            selectedUserIds(ctx).forEach(uid ->
                ctx.locals.put("chestGive:" + uid, paramLong(c, "chestId"))));
        r.registerAction("act.set_furni_direction", (c, ctx) -> {
            int dir = paramInt(c, "direction");
            selectedFurniIds(ctx).forEach(id -> ctx.room.rotateFurni(id, dir));
        });
        r.registerAction("act.add_furni_to_selector", (c, ctx) -> {
            @SuppressWarnings("unchecked")
            List<Long> sel = (List<Long>) ctx.locals.computeIfAbsent("selectedFurnis",
                k -> new ArrayList<>());
            sel.addAll(furniListParam(c));
        });
        r.registerAction("act.mute_user", (c, ctx) -> {
            int seconds = paramInt(c, "seconds");
            selectedUserIds(ctx).forEach(uid -> ctx.locals.put("muteUser:" + uid, seconds));
        });
        r.registerAction("act.no_op", (c, ctx) -> {});
    }

    // --- Helpers ---

    private static boolean matchesFurni(WiredStack.WiredComponent c, WiredContext ctx, String key) {
        Object id = ctx.locals.get(key);
        List<?> ids = (List<?>) c.params().get("furniIds");
        return id != null && (ids == null || ids.isEmpty() || ids.contains(id));
    }

    private static boolean matchesFurni(WiredStack stack, WiredContext ctx, String key) {
        return stack.getTrigger() != null && matchesFurni(stack.getTrigger(), ctx, key);
    }

    private static boolean matchesArea(WiredStack stack, WiredContext ctx, String key) {
        Object[] area = (Object[]) ctx.locals.get(key);
        if (area == null || stack.getTrigger() == null) return false;
        int x1 = paramInt(stack.getTrigger(), "x1"); int y1 = paramInt(stack.getTrigger(), "y1");
        int x2 = paramInt(stack.getTrigger(), "x2"); int y2 = paramInt(stack.getTrigger(), "y2");
        int ax = (int) area[0]; int ay = (int) area[1];
        return ax >= x1 && ax <= x2 && ay >= y1 && ay <= y2;
    }

    private static WiredValue resolveVar(WiredStack.WiredComponent c, WiredContext ctx) {
        WiredContext.Scope scope = parseScope(paramStr(c, "scope"));
        return ctx.getVariable(scope, paramStr(c, "varName"));
    }

    private static WiredValue resolveValue(WiredStack.WiredComponent c, WiredContext ctx) {
        String type = paramStr(c, "valueType");
        return switch (type) {
            case "number"   -> WiredValue.ofNumber(paramDouble(c, "value"));
            case "text"     -> WiredValue.ofText(paramStr(c, "value"));
            case "bool"     -> WiredValue.ofBool("true".equalsIgnoreCase(paramStr(c, "value")));
            case "variable" -> {
                WiredContext.Scope scope = parseScope(paramStr(c, "scope"));
                yield ctx.getVariable(scope, paramStr(c, "varName"));
            }
            default -> WiredValue.ofText(paramStr(c, "value"));
        };
    }

    @SuppressWarnings("unchecked")
    private static List<Long> selectedFurniIds(WiredContext ctx) {
        Object sel = ctx.locals.get("selectedFurnis");
        return sel instanceof List<?> l ? (List<Long>) l : List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Long> selectedUserIds(WiredContext ctx) {
        Object sel = ctx.locals.get("selectedUsers");
        return sel instanceof List<?> l ? (List<Long>) l : List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Long> furniListParam(WiredStack.WiredComponent c) {
        Object p = c.params().get("furniIds");
        if (p instanceof List<?> l) return (List<Long>) l;
        return List.of();
    }

    private static String paramStr(WiredStack.WiredComponent c, String key) {
        Object v = c.params().get(key);
        return v != null ? v.toString() : "";
    }

    private static int paramInt(WiredStack.WiredComponent c, String key) {
        Object v = c.params().get(key);
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v != null ? v.toString() : "0"); }
        catch (NumberFormatException e) { return 0; }
    }

    private static long paramLong(WiredStack.WiredComponent c, String key) {
        Object v = c.params().get(key);
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v != null ? v.toString() : "0"); }
        catch (NumberFormatException e) { return 0L; }
    }

    private static double paramDouble(WiredStack.WiredComponent c, String key) {
        Object v = c.params().get(key);
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v != null ? v.toString() : "0"); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private static WiredContext.Scope parseScope(String s) {
        return switch (s == null ? "" : s.toLowerCase()) {
            case "user"   -> WiredContext.Scope.USER;
            case "global" -> WiredContext.Scope.GLOBAL;
            default       -> WiredContext.Scope.ROOM;
        };
    }

    private static boolean isHourBetween(int from, int to) {
        int hour = java.time.LocalTime.now().getHour();
        return hour >= from && hour < to;
    }
}
