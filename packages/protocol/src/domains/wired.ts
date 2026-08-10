export const WiredPacketTypes = {
  // c2s
  TRIGGER_OPEN: 'wired.trigger.open',
  TRIGGER_SAVE: 'wired.trigger.save',
  ACTION_OPEN: 'wired.action.open',
  ACTION_SAVE: 'wired.action.save',
  CONDITION_OPEN: 'wired.condition.open',
  CONDITION_SAVE: 'wired.condition.save',
  SELECTOR_OPEN: 'wired.selector.open',
  SELECTOR_SAVE: 'wired.selector.save',
  STACK_EXPORT: 'wired.stack.export',
  STACK_IMPORT: 'wired.stack.import',
  VARIABLE_GET: 'wired.variable.get',
  VARIABLE_SET: 'wired.variable.set',
  VARIABLE_LIST: 'wired.variable.list',
  DEBUG_START: 'wired.debug.start',
  DEBUG_STOP: 'wired.debug.stop',
  DEBUG_STEP: 'wired.debug.step',
  SIGNAL_SEND: 'wired.signal.send',
  CHEST_OPEN: 'wired.chest.open',
  CHEST_SAVE: 'wired.chest.save',

  // s2c
  TRIGGER_OPENED: 'wired.trigger.opened',
  TRIGGER_SAVED: 'wired.trigger.saved',
  ACTION_OPENED: 'wired.action.opened',
  ACTION_SAVED: 'wired.action.saved',
  CONDITION_OPENED: 'wired.condition.opened',
  CONDITION_SAVED: 'wired.condition.saved',
  SELECTOR_OPENED: 'wired.selector.opened',
  SELECTOR_SAVED: 'wired.selector.saved',
  STACK_EXPORTED: 'wired.stack.exported',
  STACK_IMPORTED: 'wired.stack.imported',
  VARIABLE_RESULT: 'wired.variable.result',
  VARIABLE_LIST_RESULT: 'wired.variable.list.result',
  VARIABLE_CHANGED: 'wired.variable.changed',
  DEBUG_EVENT: 'wired.debug.event',
  DEBUG_VARIABLE_INSPECT: 'wired.debug.variable.inspect',
  SIGNAL_RECEIVED: 'wired.signal.received',
  CHEST_OPENED: 'wired.chest.opened',
  CHEST_SAVED: 'wired.chest.saved',
  EXECUTION_LOG: 'wired.execution.log',
  ERROR: 'wired.error',
} as const;

export type WiredPacketType = (typeof WiredPacketTypes)[keyof typeof WiredPacketTypes];

export type WiredTriggerType =
  | 'player_enters_room' | 'player_leaves_room' | 'player_says' | 'player_says_keyword'
  | 'player_walks_on' | 'player_walks_off' | 'player_sits' | 'player_stands'
  | 'furni_state_changed' | 'furni_clicked' | 'furni_selected'
  | 'game_starts' | 'game_ends' | 'game_score_updated'
  | 'periodically' | 'periodically_long'
  | 'score_achieved' | 'score_achieved_for_team'
  | 'toggle_furni' | 'avatar_says_to_avatar'
  | 'wired_resets' | 'signal_received';

export type WiredActionType =
  | 'move_furni' | 'rotate_furni' | 'resize_furni' | 'move_and_rotate_furni'
  | 'set_furni_state' | 'toggle_furni_state' | 'set_furni_to_random_state'
  | 'give_score' | 'give_score_to_team' | 'reset_scores'
  | 'move_avatar_to_direction' | 'teleport_to' | 'teleport_to_furni'
  | 'chase_avatar' | 'flee_avatar'
  | 'give_reward' | 'give_credits' | 'give_diamonds' | 'give_nut_points' | 'give_item'
  | 'say' | 'shout' | 'whisper' | 'bot_say' | 'bot_shout' | 'bot_whisper'
  | 'set_variable' | 'math_variable' | 'text_variable' | 'convert_variable'
  | 'send_signal' | 'send_global_signal'
  | 'kick_avatar' | 'mute_avatar' | 'unmute_avatar'
  | 'set_role_play_team' | 'reset_timer'
  | 'show_message' | 'play_sound'
  | 'call_other_stack' | 'stop_all_wired'
  | 'set_furni_direction' | 'set_furni_altitude' | 'set_furni_to_avatar_count'
  | 'join_team' | 'leave_team'
  | 'show_image' | 'hide_image'
  | 'toggle_room_block_walking' | 'toggle_room_mute'
  | 'give_achievement' | 'complete_quest'
  | 'set_npc_state' | 'trigger_random' | 'trigger_stacked'
  | 'save_variable_to_chest' | 'load_variable_from_chest'
  | 'set_furni_color' | 'set_background_color' | 'animate_furni'
  | 'camera_follow_avatar' | 'camera_set_position'
  | 'spawn_bot_at' | 'despawn_bot' | 'bot_move_to' | 'bot_set_chat_message';

export type WiredConditionType =
  | 'actor_in_group' | 'actor_not_in_group'
  | 'actor_wears_badge' | 'actor_not_wears_badge'
  | 'actor_has_rank' | 'actor_not_has_rank'
  | 'actor_is_member' | 'actor_is_not_member'
  | 'furni_matches_snapshot' | 'furni_not_matches_snapshot'
  | 'furni_has_state' | 'furni_not_has_state'
  | 'room_player_count' | 'room_player_count_range'
  | 'team_player_count' | 'actor_on_team'
  | 'date_is' | 'time_of_day_is' | 'time_elapsed_more' | 'time_elapsed_less'
  | 'variable_equals' | 'variable_greater' | 'variable_less' | 'variable_greater_eq' | 'variable_less_eq'
  | 'variable_contains' | 'variable_starts_with' | 'variable_is_empty' | 'variable_is_not_empty'
  | 'actor_stands_on' | 'actor_not_stands_on'
  | 'trigger_collides_furni' | 'random_chance'
  | 'actor_is_blocked' | 'actor_is_not_blocked'
  | 'score_equals' | 'score_greater' | 'score_less';

export type WiredSelectorType =
  | 'furni_named' | 'furni_with_state' | 'furni_near' | 'furni_random' | 'furni_all_in_range'
  | 'furni_of_type' | 'furni_touching_trigger'
  | 'avatar_named' | 'avatar_random' | 'avatar_with_badge' | 'avatar_in_team' | 'avatar_triggerer'
  | 'avatar_in_group' | 'avatar_with_rank'
  | 'all_furni_of_type' | 'random_furni_of_type'
  | 'nearest_furni' | 'farthest_furni'
  | 'bot_named' | 'bot_random'
  | 'pet_named' | 'pet_random'
  | 'furni_intersecting_path' | 'furni_with_id';

export type WiredVarScope = 'room' | 'user' | 'global';
export type WiredVarType = 'number' | 'text' | 'bool';

export interface WiredVariable {
  name: string;
  scope: WiredVarScope;
  type: WiredVarType;
  value: number | string | boolean;
  updatedAt: string;
}

export interface WiredTriggerConfig {
  furniIds: number[];
  stringParam?: string;
  intParam?: number;
  delayMs?: number;
}

export interface WiredActionConfig {
  furniIds: number[];
  stringParam?: string;
  intParam?: number;
  delayMs?: number;
  variableName?: string;
  variableScope?: WiredVarScope;
  expression?: string;
}

export interface WiredConditionConfig {
  furniIds: number[];
  stringParam?: string;
  intParam?: number;
  negate?: boolean;
  variableName?: string;
  variableScope?: WiredVarScope;
  operand?: number | string | boolean;
}

export interface WiredSelectorConfig {
  furniIds: number[];
  stringParam?: string;
  intParam?: number;
}

export interface WiredDebugEvent {
  triggerId: number;
  stackId: string;
  executionId: string;
  event: 'trigger_fired' | 'condition_pass' | 'condition_fail' | 'action_executed' | 'variable_changed' | 'signal_sent' | 'error';
  data: Record<string, unknown>;
  timestampMs: number;
}

export interface WiredStackExport {
  version: 1;
  exportedAt: string;
  items: Array<{
    furniId: number;
    type: 'trigger' | 'action' | 'condition' | 'selector';
    wiredType: string;
    config: WiredTriggerConfig | WiredActionConfig | WiredConditionConfig | WiredSelectorConfig;
    relativeX: number;
    relativeY: number;
  }>;
  variables: WiredVariable[];
}
