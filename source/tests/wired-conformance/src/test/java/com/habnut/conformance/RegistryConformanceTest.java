package com.habnut.conformance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The completeness gate: every block named in the specification must be
 * registered, and the registry must contain nothing beyond the specification.
 *
 * A block that is renamed, dropped or silently added fails here rather than at
 * runtime in a player's room.
 */
@DisplayName("Wired registry completeness")
class RegistryConformanceTest {

    private static final WiredHarness H = new WiredHarness();

    static final List<String> TRIGGERS = List.of(
        "trigger.walk_on_furni", "trigger.walk_off_furni", "trigger.enter_room",
        "trigger.leave_room", "trigger.say", "trigger.say_contains",
        "trigger.furni_state_changed", "trigger.score_achieved", "trigger.timer",
        "trigger.game_starts", "trigger.game_ends", "trigger.collision",
        "trigger.avatar_enters_area", "trigger.avatar_leaves_area", "trigger.toggle_furni",
        "trigger.bot_reaches_furni", "trigger.bot_reached_avat", "trigger.signal_received",
        "trigger.variable_changed", "trigger.periodic_long", "trigger.periodic_short");

    static final List<String> CONDITIONS = List.of(
        "cond.actor_in_group", "cond.not_actor_in_group", "cond.furni_has_users_on",
        "cond.furni_has_no_users", "cond.actor_on_furni", "cond.not_actor_on_furni",
        "cond.furni_state_is", "cond.furni_state_is_not", "cond.var_eq", "cond.var_not_eq",
        "cond.var_gt", "cond.var_lt", "cond.var_gte", "cond.var_lte", "cond.var_contains",
        "cond.not_var_contains", "cond.date_range_active", "cond.actor_is_room_owner",
        "cond.actor_has_room_rights", "cond.not_actor_has_room_rights", "cond.team_wins",
        "cond.not_team_wins", "cond.score_gte", "cond.score_lt", "cond.trigger_on_furni",
        "cond.not_trigger_on_furni", "cond.actor_in_team", "cond.not_actor_in_team",
        "cond.user_count_gte", "cond.user_count_lt", "cond.signal_value_eq",
        "cond.var_is_type", "cond.always_true", "cond.always_false", "cond.random_chance",
        "cond.is_day", "cond.is_night");

    static final List<String> SELECTORS = List.of(
        "sel.random_furni", "sel.all_furni", "sel.specific_furnis", "sel.furni_in_room",
        "sel.users_on_furni", "sel.users_in_area", "sel.all_users", "sel.actor",
        "sel.furni_by_type", "sel.furni_with_state", "sel.closest_furni", "sel.first_furni",
        "sel.last_furni", "sel.nth_furni", "sel.trigger_furni", "sel.users_in_team",
        "sel.users_with_badge", "sel.bots_in_room", "sel.selected", "sel.random_users",
        "sel.not_actor", "sel.empty");

    static final List<String> ACTIONS = List.of(
        "act.move_furni", "act.set_furni_state", "act.toggle_furni_state", "act.move_avatar_to",
        "act.teleport_user", "act.kick_user", "act.give_badge", "act.give_credits",
        "act.give_nut_points", "act.chat", "act.chat_to_user", "act.set_room_owner_effect",
        "act.reset_furni", "act.rotate_furni", "act.furni_to_user", "act.user_to_furni",
        "act.give_score", "act.add_team_score", "act.set_var", "act.add_var", "act.sub_var",
        "act.mul_var", "act.div_var", "act.mod_var", "act.reset_var", "act.var_to_chat",
        "act.concat_var", "act.emit_signal", "act.emit_global_signal", "act.set_room_lighting",
        "act.set_room_music", "act.show_notification", "act.set_floor_item_data",
        "act.toggle_floor_item_sticky_pole", "act.move_and_rotate_furni", "act.set_avatar_effect",
        "act.sit_user", "act.stand_user", "act.wave_user", "act.dance_user", "act.start_game",
        "act.end_game", "act.reset_game", "act.toggle_game", "act.freeze_user",
        "act.unfreeze_user", "act.move_bot", "act.bot_chat", "act.open_link", "act.set_timer",
        "act.cancel_timer", "act.chest_give_item", "act.set_furni_direction",
        "act.add_furni_to_selector", "act.mute_user", "act.no_op");

    @Test
    @DisplayName("counts match the specification")
    void counts() {
        assertEquals(21, TRIGGERS.size(),   "trigger count");
        assertEquals(37, CONDITIONS.size(), "condition count");
        assertEquals(22, SELECTORS.size(),  "selector count");
        assertEquals(56, ACTIONS.size(),    "action count");
    }

    @Test
    @DisplayName("no duplicate codes within a block type")
    void noDuplicates() {
        assertEquals(TRIGGERS.size(),   TRIGGERS.stream().distinct().count(),   "duplicate trigger");
        assertEquals(CONDITIONS.size(), CONDITIONS.stream().distinct().count(), "duplicate condition");
        assertEquals(SELECTORS.size(),  SELECTORS.stream().distinct().count(),  "duplicate selector");
        assertEquals(ACTIONS.size(),    ACTIONS.stream().distinct().count(),    "duplicate action");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("triggers")
    @DisplayName("every trigger is registered")
    void triggerRegistered(String code) {
        assertTrue(H.registry.hasTrigger(code), "trigger not registered: " + code);
        assertNotNull(H.registry.getTrigger(code));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("conditions")
    @DisplayName("every condition is registered")
    void conditionRegistered(String code) {
        assertTrue(H.registry.hasCondition(code), "condition not registered: " + code);
        assertNotNull(H.registry.getCondition(code));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("selectors")
    @DisplayName("every selector is registered")
    void selectorRegistered(String code) {
        assertTrue(H.registry.hasSelector(code), "selector not registered: " + code);
        assertNotNull(H.registry.getSelector(code));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("actions")
    @DisplayName("every action is registered")
    void actionRegistered(String code) {
        assertTrue(H.registry.hasAction(code), "action not registered: " + code);
        assertNotNull(H.registry.getAction(code));
    }

    @Test
    @DisplayName("unknown codes resolve to nothing")
    void unknownCodes() {
        assertFalse(H.registry.hasTrigger("trigger.does_not_exist"));
        assertFalse(H.registry.hasCondition("cond.does_not_exist"));
        assertFalse(H.registry.hasSelector("sel.does_not_exist"));
        assertFalse(H.registry.hasAction("act.does_not_exist"));
        assertNull(H.registry.getAction("act.does_not_exist"));
    }

    static List<String> triggers()   { return TRIGGERS; }
    static List<String> conditions() { return CONDITIONS; }
    static List<String> selectors()  { return SELECTORS; }
    static List<String> actions()    { return ACTIONS; }
}
