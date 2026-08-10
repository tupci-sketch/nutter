export const SocialPacketTypes = {
  // c2s friends
  FRIEND_LIST: 'social.friend.list',
  FRIEND_REQUEST_SEND: 'social.friend.request.send',
  FRIEND_REQUEST_ACCEPT: 'social.friend.request.accept',
  FRIEND_REQUEST_DECLINE: 'social.friend.request.decline',
  FRIEND_REMOVE: 'social.friend.remove',
  FRIEND_REQUESTS: 'social.friend.requests',
  FRIEND_SEARCH: 'social.friend.search',

  // c2s blocking
  BLOCK: 'social.block',
  UNBLOCK: 'social.unblock',
  BLOCK_LIST: 'social.block.list',

  // c2s messaging
  MSG_SEND: 'social.msg.send',
  MSG_LIST: 'social.msg.list',
  MSG_DELETE: 'social.msg.delete',
  MSG_MARK_READ: 'social.msg.read',

  // s2c
  FRIEND_LIST_RESULT: 'social.friend.list.result',
  FRIEND_ONLINE: 'social.friend.online',
  FRIEND_OFFLINE: 'social.friend.offline',
  FRIEND_STATUS_UPDATE: 'social.friend.status.update',
  FRIEND_ADDED: 'social.friend.added',
  FRIEND_REMOVED: 'social.friend.removed',
  FRIEND_REQUEST_RECEIVED: 'social.friend.request.received',
  FRIEND_REQUEST_ACCEPTED: 'social.friend.request.accepted',
  FRIEND_REQUESTS_RESULT: 'social.friend.requests.result',
  FRIEND_SEARCH_RESULT: 'social.friend.search.result',
  BLOCKED: 'social.blocked',
  BLOCK_LIST_RESULT: 'social.block.list.result',
  MSG_RECEIVED: 'social.msg.received',
  MSG_LIST_RESULT: 'social.msg.list.result',
  MSG_SENT: 'social.msg.sent',
  ERROR: 'social.error',
} as const;

export type SocialPacketType = (typeof SocialPacketTypes)[keyof typeof SocialPacketTypes];

export interface FriendEntry {
  userId: number;
  username: string;
  figureString: string;
  motto: string;
  online: boolean;
  roomId: number | null;
  roomName: string | null;
  lastSeen: string | null;
}

export interface SocialMessage {
  id: number;
  fromUserId: number;
  fromUsername: string;
  fromFigure: string;
  toUserId: number;
  body: string;
  sentAt: string;
  readAt: string | null;
}

export interface SocialFriendRequestSendPayload {
  username: string;
}

export interface SocialFriendRequestAcceptPayload {
  fromUserId: number;
}

export interface SocialFriendRequestDeclinePayload {
  fromUserId: number;
  blockFuture?: boolean;
}

export interface SocialFriendRemovePayload {
  friendUserId: number;
}

export interface SocialBlockPayload {
  targetUserId: number;
}

export interface SocialMsgSendPayload {
  toUserId: number;
  body: string;
  idempotencyKey: string;
}

export interface SocialFriendListResultPayload {
  friends: FriendEntry[];
  total: number;
  unreadMessageCount: number;
}

export interface SocialFriendStatusUpdatePayload {
  userId: number;
  online: boolean;
  roomId: number | null;
  roomName: string | null;
  figureString: string;
  motto: string;
}

export interface SocialMsgListPayload {
  page: number;
  pageSize: number;
  unreadOnly?: boolean;
}

export interface SocialMsgListResultPayload {
  messages: SocialMessage[];
  total: number;
  page: number;
}
