export const ModPacketTypes = {
  // c2s
  REPORT_CREATE: 'mod.report.create',
  REPORT_LIST: 'mod.report.list',
  REPORT_CLAIM: 'mod.report.claim',
  REPORT_RESOLVE: 'mod.report.resolve',
  USER_MUTE: 'mod.user.mute',
  USER_UNMUTE: 'mod.user.unmute',
  USER_KICK: 'mod.user.kick',
  USER_BAN: 'mod.user.ban',
  USER_UNBAN: 'mod.user.unban',
  USER_WARN: 'mod.user.warn',
  ROOM_MUTE: 'mod.room.mute',
  ROOM_UNMUTE: 'mod.room.unmute',
  USER_INFO: 'mod.user.info',
  CHAT_LOGS: 'mod.chat.logs',
  ACTION_HISTORY: 'mod.action.history',
  WORD_FILTER_LIST: 'mod.word_filter.list',
  WORD_FILTER_ADD: 'mod.word_filter.add',
  WORD_FILTER_REMOVE: 'mod.word_filter.remove',
  APPEAL_SUBMIT: 'mod.appeal.submit',
  APPEAL_LIST: 'mod.appeal.list',
  APPEAL_REVIEW: 'mod.appeal.review',
  AUTO_MUTE_STATE: 'mod.automute.state',
  AUTO_MUTE_HELP: 'mod.automute.help',

  // s2c
  REPORT_CREATED: 'mod.report.created',
  REPORT_LIST_RESULT: 'mod.report.list.result',
  REPORT_CLAIMED: 'mod.report.claimed',
  REPORT_RESOLVED: 'mod.report.resolved',
  USER_MUTED: 'mod.user.muted',
  USER_KICKED: 'mod.user.kicked',
  USER_BANNED: 'mod.user.banned',
  USER_UNBANNED: 'mod.user.unbanned',
  USER_WARNED: 'mod.user.warned',
  ROOM_MUTED: 'mod.room.muted',
  USER_INFO_RESULT: 'mod.user.info.result',
  CHAT_LOGS_RESULT: 'mod.chat.logs.result',
  ACTION_HISTORY_RESULT: 'mod.action.history.result',
  WORD_FILTER_LIST_RESULT: 'mod.word_filter.list.result',
  WORD_FILTER_UPDATED: 'mod.word_filter.updated',
  REPORT_ALERT: 'mod.report.alert',
  CALL_FOR_HELP: 'mod.call_for_help',
  APPEAL_SUBMITTED: 'mod.appeal.submitted',
  APPEAL_LIST_RESULT: 'mod.appeal.list.result',
  APPEAL_REVIEWED: 'mod.appeal.reviewed',
  AUTO_MUTE_NOTICE: 'mod.automute.notice',
  AUTO_MUTE_HELP_ACK: 'mod.automute.help.ack',
  AUTO_MUTE_ALERT: 'mod.automute.alert',
  ERROR: 'mod.error',
} as const;

export type ModPacketType = (typeof ModPacketTypes)[keyof typeof ModPacketTypes];

export type ModActionType = 'mute' | 'unmute' | 'kick' | 'ban' | 'unban' | 'warn' | 'room_mute' | 'room_unmute';
export type ReportCategory = 'offensive_language' | 'harassment' | 'scamming' | 'cheating' | 'inappropriate_content' | 'ban_evasion' | 'other';
export type BanType = 'standard' | 'ip' | 'machine' | 'account_family';

export interface ModReport {
  id: number;
  reporterId: number;
  reporterName: string;
  targetId: number;
  targetName: string;
  category: ReportCategory;
  description: string;
  chatContext: string[];
  roomId: number | null;
  roomName: string | null;
  status: 'open' | 'claimed' | 'resolved' | 'dismissed';
  claimedById: number | null;
  claimedByName: string | null;
  resolution: string | null;
  createdAt: string;
  resolvedAt: string | null;
}

export interface ModAction {
  id: number;
  performedById: number;
  performedByName: string;
  targetId: number;
  targetName: string;
  actionType: ModActionType;
  reason: string;
  durationMs: number | null;
  expiresAt: string | null;
  roomId: number | null;
  createdAt: string;
}

export interface ModUserInfo {
  userId: number;
  username: string;
  email: string;
  figureString: string;
  rank: number;
  rankName: string;
  registeredAt: string;
  lastLogin: string | null;
  lastIp: string | null;
  machineIds: string[];
  activeBan: {
    reason: string;
    banType: BanType;
    expiresAt: string | null;
    bannedById: number;
    bannedByName: string;
  } | null;
  activeMute: {
    reason: string;
    expiresAt: string | null;
  } | null;
  recentActions: ModAction[];
  reportCount: number;
  banCount: number;
  muteCount: number;
  credits: number;
  diamonds: number;
}

export interface ModReportCreatePayload {
  targetId: number;
  category: ReportCategory;
  description: string;
  chatContext?: string[];
}

export interface ModUserBanPayload {
  targetId: number;
  reason: string;
  banType: BanType;
  durationHours: number | null;
}

export interface ModUserMutePayload {
  targetId: number;
  reason: string;
  durationMinutes: number;
  roomScoped: boolean;
}

export interface ModWordFilterEntry {
  id: number;
  word: string;
  severity: 1 | 2 | 3;
  action: 'block' | 'replace' | 'log';
  replacement: string | null;
}

export interface ModAppealSubmitPayload {
  banId: number;
  message: string;
}

export interface ModChatLogsPayload {
  targetId: number;
  fromTimestamp?: string;
  toTimestamp?: string;
  roomId?: number;
  limit: number;
}

export interface ChatLogEntry {
  userId: number;
  username: string;
  roomId: number;
  roomName: string;
  message: string;
  timestamp: string;
  type: 'chat' | 'shout' | 'whisper' | 'command';
}

/** Why the content policy stopped a message. */
export type AutoMuteCategory =
  | 'hate'
  | 'threat'
  | 'minor_safety'
  | 'doxxing'
  | 'self_harm'
  | 'scam';

/**
 * Sent to a player the content policy has just muted.
 *
 * The category is deliberately coarse: telling somebody exactly which
 * expression caught them is a manual for getting around it.
 */
export interface ModAutoMuteNoticePayload {
  caseId: number;
  category: AutoMuteCategory;
  /** When the mute lifts on its own if nobody has reviewed it by then. */
  expiresAt: string;
  /** True when the player may ask for a person to look at it now. */
  canAskForHelp: boolean;
  message: string;
}

/** The player's own view of an automatic mute, and whether they may ask again. */
export interface ModAutoMuteStatePayload {
  muted: boolean;
  automatic: boolean;
  category?: AutoMuteCategory;
  expiresAt?: string;
  reason?: string;
  canAskForHelp: boolean;
  /** Seconds until another help request is allowed; zero when one is. */
  secondsUntilHelpAllowed: number;
}

/** A muted player asking a staff member to look at why. */
export interface ModAutoMuteHelpPayload {
  message: string;
}

export interface ModAutoMuteHelpAckPayload {
  sent: boolean;
  /** Present when the request was refused for being too soon. */
  secondsUntilHelpAllowed?: number;
  message: string;
}

/** Raised to on-duty staff when the policy mutes somebody. */
export interface ModAutoMuteAlertPayload {
  caseId: number;
  userId: number;
  username: string;
  category: AutoMuteCategory;
  roomId: number | null;
  pendingCases: number;
}
