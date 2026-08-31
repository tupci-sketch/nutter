export const BotPacketTypes = {
  // c2s
  PLACE: 'bot.place',
  PICKUP: 'bot.pickup',
  UPDATE: 'bot.update',
  COMMAND: 'bot.command',

  // s2c
  PLACED: 'bot.placed',
  PICKED_UP: 'bot.picked_up',
  MOVED: 'bot.moved',
  CHAT: 'bot.chat',
  UPDATED: 'bot.updated',
  ERROR: 'bot.error',
} as const;

export type BotPacketType = (typeof BotPacketTypes)[keyof typeof BotPacketTypes];

export type BotChatMode = 'disabled' | 'random' | 'on_walk_on' | 'reaction';
export type BotWalkMode = 'stand' | 'random_walk' | 'walk_to_avatar' | 'path';

export interface BotData {
  id: number;
  name: string;
  motto: string;
  figureString: string;
  gender: 'M' | 'F';
  x: number;
  y: number;
  direction: number;
  chatMode: BotChatMode;
  walkMode: BotWalkMode;
  chatLines: string[];
  chatDelayMs: number;
  ownerId: number;
}

export interface BotPlacePayload {
  botId: number;
  x: number;
  y: number;
  direction: number;
}

export interface BotUpdatePayload {
  botId: number;
  name?: string;
  motto?: string;
  figureString?: string;
  gender?: 'M' | 'F';
  chatMode?: BotChatMode;
  walkMode?: BotWalkMode;
  chatLines?: string[];
  chatDelayMs?: number;
}

export interface BotCommandPayload {
  botId: number;
  command: 'say' | 'shout' | 'whisper' | 'move_to' | 'set_dance' | 'set_carry';
  param: string | number;
}
