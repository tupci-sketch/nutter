import { z } from 'zod';

export const GameTypeSchema = z.enum([
  'football',
  'battleball',
  'freeze',
  'racing',
  'telephrase',
  'obstacle',
  'maze',
  'team',
  'hide_seek',
  'trivia',
  'casino',
  'seasonal',
]);

export type GameType = z.infer<typeof GameTypeSchema>;

export const GameStateSchema = z.enum(['lobby', 'countdown', 'active', 'finished', 'cancelled']);
export type GameState = z.infer<typeof GameStateSchema>;

export const GameTickRateMs = 100;
export const GameCountdownSeconds = 5;
export const FootballMatchDurationMs = 3 * 60 * 1000;
export const BattleballMatchDurationMs = 3 * 60 * 1000;
export const FreezeMatchDurationMs = 5 * 60 * 1000;
export const RacingLaps = 3;
export const TelephraseTurns = 10;

export const GameTeamColors = {
  a: 0xff4444,
  b: 0x4444ff,
  spectator: 0x888888,
} as const;

export interface GameStats {
  userId: number;
  gameType: GameType;
  matchId: string;
  score: number;
  kills: number;
  deaths: number;
  assists: number;
  won: boolean;
  teamId: 'a' | 'b' | null;
  durationMs: number;
  recordedAt: string;
}

export const GAME_MIN_PLAYERS: Partial<Record<GameType, number>> = {
  football: 2,
  battleball: 2,
  freeze: 2,
  racing: 2,
  telephrase: 2,
  trivia: 2,
};

export const GAME_MAX_PLAYERS: Partial<Record<GameType, number>> = {
  football: 10,
  battleball: 25,
  freeze: 25,
  racing: 8,
  telephrase: 25,
  trivia: 25,
  casino: 10,
};
