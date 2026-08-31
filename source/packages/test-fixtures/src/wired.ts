export interface FixtureWiredItem {
  id: number;
  room_id: number;
  furni_id: number;
  kind: 'trigger' | 'action' | 'condition' | 'selector';
  type_id: number;
  data: Record<string, unknown>;
}

export const FIXTURE_WIRED_TRIGGER: FixtureWiredItem = {
  id: 1,
  room_id: 1,
  furni_id: 100,
  kind: 'trigger',
  type_id: 1, // ON_USER_ENTER
  data: {},
};

export const FIXTURE_WIRED_ACTION: FixtureWiredItem = {
  id: 2,
  room_id: 1,
  furni_id: 101,
  kind: 'action',
  type_id: 1, // CHAT
  data: { message: 'Hello from Wired!', chat_type: 'say' },
};

export const FIXTURE_WIRED_CONDITION: FixtureWiredItem = {
  id: 3,
  room_id: 1,
  furni_id: 102,
  kind: 'condition',
  type_id: 1, // USER_COUNT_IN_ROOM
  data: { operator: 'GTE', value: 1 },
};

export const FIXTURE_WIRED_VARIABLE = {
  room_id: 1,
  scope: 'room' as const,
  name: 'counter',
  type: 'number' as const,
  value: '0',
};

export const FIXTURE_WIRED_STACK = [
  FIXTURE_WIRED_TRIGGER,
  FIXTURE_WIRED_CONDITION,
  FIXTURE_WIRED_ACTION,
];
