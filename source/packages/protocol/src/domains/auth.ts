export const AuthPacketTypes = {
  // c2s
  LOGIN: 'auth.login',
  LOGOUT: 'auth.logout',
  WORLD_SWITCH: 'auth.world.switch',
  PING: 'auth.ping',

  // s2c
  LOGIN_SUCCESS: 'auth.login.success',
  LOGIN_ERROR: 'auth.login.error',
  LOGOUT_SUCCESS: 'auth.logout.success',
  WORLD_SWITCHED: 'auth.world.switched',
  WORLD_SWITCH_ERROR: 'auth.world.switch.error',
  PONG: 'auth.pong',
  SESSION_EXPIRED: 'auth.session.expired',
  KICKED: 'auth.kicked',
} as const;

export type AuthPacketType = (typeof AuthPacketTypes)[keyof typeof AuthPacketTypes];

export interface AuthLoginPayload {
  ticket: string;
  machineId?: string;
  clientVersion?: string;
}

export interface AuthLoginSuccessPayload {
  userId: number;
  username: string;
  figureString: string;
  rank: number;
  credits: number;
  diamonds: number;
  nutPoints: number;
  lastLogin: string | null;
  worldId: string;
}

export interface AuthLoginErrorPayload {
  code: string;
  reason: string;
  banExpiry?: string | null;
}

export interface AuthWorldSwitchPayload {
  worldId: 'classic' | 'nutropolis';
}

export interface AuthWorldSwitchedPayload {
  worldId: 'classic' | 'nutropolis';
  sessionToken: string;
}

export interface AuthKickedPayload {
  reason: string;
  moderatorName?: string;
}
