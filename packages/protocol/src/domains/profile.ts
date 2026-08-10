export const ProfilePacketTypes = {
  // c2s
  VIEW: 'profile.view',
  UPDATE_MOTTO: 'profile.motto.update',
  UPDATE_FIGURE: 'profile.figure.update',
  UPDATE_EMAIL: 'profile.email.update',
  UPDATE_PASSWORD: 'profile.password.update',
  SETUP_2FA: 'profile.2fa.setup',
  CONFIRM_2FA: 'profile.2fa.confirm',
  DISABLE_2FA: 'profile.2fa.disable',
  BADGE_ORDER: 'profile.badge.order',
  ROOM_VISITS: 'profile.room_visits',
  FRIEND_LIST: 'profile.friend_list',
  GROUP_LIST: 'profile.group_list',
  ACHIEVEMENT_LIST: 'profile.achievement_list',
  RELATION_SET: 'profile.relation.set',

  // s2c
  VIEW_RESULT: 'profile.view.result',
  MOTTO_UPDATED: 'profile.motto.updated',
  FIGURE_UPDATED: 'profile.figure.updated',
  EMAIL_UPDATED: 'profile.email.updated',
  PASSWORD_UPDATED: 'profile.password.updated',
  TWO_FA_SETUP_RESULT: 'profile.2fa.setup.result',
  TWO_FA_CONFIRMED: 'profile.2fa.confirmed',
  TWO_FA_DISABLED: 'profile.2fa.disabled',
  BADGE_ORDER_UPDATED: 'profile.badge.order.updated',
  ROOM_VISITS_RESULT: 'profile.room_visits.result',
  GROUP_LIST_RESULT: 'profile.group_list.result',
  ACHIEVEMENT_LIST_RESULT: 'profile.achievement_list.result',
  ERROR: 'profile.error',
} as const;

export type ProfilePacketType = (typeof ProfilePacketTypes)[keyof typeof ProfilePacketTypes];

export interface ProfileViewPayload {
  userId: number;
}

export interface ProfileData {
  userId: number;
  username: string;
  figureString: string;
  motto: string;
  rankName: string;
  memberSince: string;
  lastSeen: string | null;
  roomVisits: number;
  achievementScore: number;
  friendCount: number;
  groupCount: number;
  badges: Array<{
    id: string;
    name: string;
    description: string;
    spriteId: string;
    slotIndex: number | null;
  }>;
  relations: Array<{
    targetUserId: number;
    targetUsername: string;
    targetFigure: string;
    relationType: number;
  }>;
  isFriend: boolean;
  isBlocked: boolean;
  inRoom: boolean;
  currentRoomId: number | null;
  currentRoomName: string | null;
  membership: {
    tier: string;
    expiresAt: string | null;
    badgeId: string | null;
  };
}

export interface ProfileUpdateMottoPayload {
  motto: string;
}

export interface ProfileUpdateFigurePayload {
  figureString: string;
  gender: 'M' | 'F';
}

export interface ProfileUpdateEmailPayload {
  email: string;
  currentPassword: string;
}

export interface ProfileUpdatePasswordPayload {
  currentPassword: string;
  newPassword: string;
}

export interface Profile2FASetupResultPayload {
  qrCodeUri: string;
  backupCodes: string[];
}

export interface ProfileRelationSetPayload {
  targetUserId: number;
  relationType: 0 | 1 | 2 | 3;
}
