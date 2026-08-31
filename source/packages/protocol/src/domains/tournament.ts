export const TournamentPacketTypes = {
  // c2s
  LIST: 'tournament.list',
  INFO: 'tournament.info',
  REGISTER: 'tournament.register',
  UNREGISTER: 'tournament.unregister',
  BRACKET: 'tournament.bracket',

  // s2c
  LIST_RESULT: 'tournament.list.result',
  INFO_RESULT: 'tournament.info.result',
  REGISTERED: 'tournament.registered',
  UNREGISTERED: 'tournament.unregistered',
  BRACKET_RESULT: 'tournament.bracket.result',
  MATCH_SCHEDULED: 'tournament.match.scheduled',
  MATCH_STARTED: 'tournament.match.started',
  MATCH_ENDED: 'tournament.match.ended',
  ROUND_ADVANCED: 'tournament.round.advanced',
  ELIMINATED: 'tournament.eliminated',
  CHAMPION: 'tournament.champion',
  CANCELLED: 'tournament.cancelled',
  ERROR: 'tournament.error',
} as const;

export type TournamentPacketType = (typeof TournamentPacketTypes)[keyof typeof TournamentPacketTypes];

export type TournamentStatus = 'upcoming' | 'registration' | 'active' | 'finished' | 'cancelled';
export type TournamentFormat = 'single_elimination' | 'double_elimination' | 'round_robin' | 'swiss';

export interface TournamentInfo {
  id: number;
  name: string;
  description: string;
  gameType: string;
  format: TournamentFormat;
  status: TournamentStatus;
  registrationOpenAt: string;
  registrationCloseAt: string;
  startsAt: string;
  maxParticipants: number;
  currentParticipants: number;
  prizeCredits: number;
  prizeDiamonds: number;
  prizeBadgeId: string | null;
  roomId: number | null;
  isRegistered: boolean;
}

export interface BracketMatch {
  matchId: number;
  round: number;
  playerA: { userId: number; username: string } | null;
  playerB: { userId: number; username: string } | null;
  winnerId: number | null;
  scoreA: number | null;
  scoreB: number | null;
  scheduledAt: string | null;
  completedAt: string | null;
}

export interface TournamentBracketResultPayload {
  tournamentId: number;
  format: TournamentFormat;
  currentRound: number;
  totalRounds: number;
  matches: BracketMatch[];
}

export interface TournamentMatchScheduledPayload {
  tournamentId: number;
  matchId: number;
  opponentId: number;
  opponentName: string;
  scheduledAt: string;
  roomId: number;
}
