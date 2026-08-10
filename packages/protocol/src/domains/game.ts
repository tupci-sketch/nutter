export const GamePacketTypes = {
  // c2s
  JOIN: 'game.join',
  LEAVE: 'game.leave',
  READY: 'game.ready',
  INPUT: 'game.input',
  TEAM_SELECT: 'game.team.select',

  // Football-specific
  FOOTBALL_KICK: 'game.football.kick',

  // Battleball-specific
  BATTLEBALL_JUMP: 'game.battleball.jump',

  // Freeze-specific
  FREEZE_THROW: 'game.freeze.throw',
  FREEZE_DODGE: 'game.freeze.dodge',

  // Racing-specific
  RACING_STEER: 'game.racing.steer',
  RACING_BOOST: 'game.racing.boost',

  // Telephrase-specific
  TELEPHRASE_GUESS: 'game.telephrase.guess',

  // Trivia-specific
  TRIVIA_ANSWER: 'game.trivia.answer',

  // Casino-specific
  CASINO_BET: 'game.casino.bet',

  // s2c
  LOBBY_STATE: 'game.lobby.state',
  STARTED: 'game.started',
  ENDED: 'game.ended',
  TICK: 'game.tick',
  SCORE_UPDATE: 'game.score.update',
  PLAYER_JOINED: 'game.player.joined',
  PLAYER_LEFT: 'game.player.left',
  PLAYER_READY: 'game.player.ready',
  TEAM_ASSIGNED: 'game.team.assigned',
  COUNTDOWN: 'game.countdown',

  FOOTBALL_STATE: 'game.football.state',
  FOOTBALL_GOAL: 'game.football.goal',
  FOOTBALL_BALL_MOVED: 'game.football.ball.moved',
  FOOTBALL_BALL_KICKED: 'game.football.ball.kicked',

  BATTLEBALL_STATE: 'game.battleball.state',
  BATTLEBALL_TILE_CLAIMED: 'game.battleball.tile.claimed',
  BATTLEBALL_PLAYER_FELL: 'game.battleball.player.fell',

  FREEZE_STATE: 'game.freeze.state',
  FREEZE_PLAYER_FROZEN: 'game.freeze.player.frozen',
  FREEZE_PLAYER_THAWED: 'game.freeze.player.thawed',
  FREEZE_POWER_UP: 'game.freeze.power_up',

  RACING_STATE: 'game.racing.state',
  RACING_CHECKPOINT: 'game.racing.checkpoint',

  TELEPHRASE_STATE: 'game.telephrase.state',
  TELEPHRASE_CORRECT: 'game.telephrase.correct',

  TRIVIA_QUESTION: 'game.trivia.question',
  TRIVIA_RESULT: 'game.trivia.result',

  CASINO_RESULT: 'game.casino.result',

  LEADERBOARD: 'game.leaderboard',
  STATS: 'game.stats',
  ERROR: 'game.error',
} as const;

export type GamePacketType = (typeof GamePacketTypes)[keyof typeof GamePacketTypes];

export type GameType = 'football' | 'battleball' | 'freeze' | 'racing' | 'telephrase' | 'obstacle' | 'maze' | 'team' | 'hide_seek' | 'trivia' | 'casino' | 'seasonal';
export type GameState = 'lobby' | 'countdown' | 'active' | 'finished' | 'cancelled';
export type GameTeam = 'a' | 'b' | 'spectator';

export interface GamePlayer {
  userId: number;
  username: string;
  figureString: string;
  team: GameTeam;
  ready: boolean;
  score: number;
  x: number;
  y: number;
  direction: number;
}

export interface GameJoinPayload {
  roomId: number;
  gameType: GameType;
  team?: GameTeam;
}

export interface GameInputPayload {
  action: string;
  data: Record<string, unknown>;
}

export interface FootballKickPayload {
  targetX: number;
  targetY: number;
  power: number;
}

export interface FootballStatePayload {
  matchId: string;
  state: GameState;
  players: GamePlayer[];
  ball: { x: number; y: number; velocityX: number; velocityY: number };
  scoreA: number;
  scoreB: number;
  timeRemainingMs: number;
}

export interface FootballGoalPayload {
  scoringTeam: 'a' | 'b';
  scoredByUserId: number;
  scoreA: number;
  scoreB: number;
}

export interface GameEndedPayload {
  matchId: string;
  state: GameState;
  finalScores: Record<string, number>;
  winner: 'a' | 'b' | 'draw' | null;
  statsGranted: Array<{ userId: number; xpGranted: number }>;
  rewardsGranted: Array<{ userId: number; creditsGranted: number }>;
}

export interface GameLeaderboardPayload {
  gameType: GameType;
  period: 'all_time' | 'season' | 'week';
  entries: Array<{
    rank: number;
    userId: number;
    username: string;
    figureString: string;
    score: number;
  }>;
}
