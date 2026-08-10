export const GroupPacketTypes = {
  // c2s
  CREATE: 'group.create',
  INFO: 'group.info',
  SEARCH: 'group.search',
  JOIN: 'group.join',
  LEAVE: 'group.leave',
  INVITE: 'group.invite',
  ACCEPT_INVITE: 'group.invite.accept',
  DECLINE_INVITE: 'group.invite.decline',
  MEMBER_LIST: 'group.members',
  MEMBER_REMOVE: 'group.member.remove',
  MEMBER_PROMOTE: 'group.member.promote',
  MEMBER_DEMOTE: 'group.member.demote',
  REQUEST_LIST: 'group.requests',
  REQUEST_ACCEPT: 'group.request.accept',
  REQUEST_DECLINE: 'group.request.decline',
  UPDATE_SETTINGS: 'group.settings.update',
  UPDATE_BADGE: 'group.badge.update',
  FORUM_THREAD_LIST: 'group.forum.threads',
  FORUM_THREAD_CREATE: 'group.forum.thread.create',
  FORUM_THREAD_READ: 'group.forum.thread.read',
  FORUM_POST_CREATE: 'group.forum.post.create',
  FORUM_POST_DELETE: 'group.forum.post.delete',
  FORUM_THREAD_PIN: 'group.forum.thread.pin',
  FORUM_THREAD_LOCK: 'group.forum.thread.lock',
  FORUM_THREAD_HIDE: 'group.forum.thread.hide',
  DELETE: 'group.delete',

  // s2c
  CREATED: 'group.created',
  INFO_RESULT: 'group.info.result',
  SEARCH_RESULT: 'group.search.result',
  JOINED: 'group.joined',
  LEFT: 'group.left',
  INVITED: 'group.invited',
  MEMBER_LIST_RESULT: 'group.members.result',
  MEMBER_REMOVED: 'group.member.removed',
  MEMBER_UPDATED: 'group.member.updated',
  REQUEST_LIST_RESULT: 'group.requests.result',
  SETTINGS_UPDATED: 'group.settings.updated',
  BADGE_UPDATED: 'group.badge.updated',
  FORUM_THREAD_LIST_RESULT: 'group.forum.threads.result',
  FORUM_THREAD_RESULT: 'group.forum.thread.result',
  FORUM_POST_CREATED: 'group.forum.post.created',
  FORUM_POST_DELETED: 'group.forum.post.deleted',
  DELETED: 'group.deleted',
  ERROR: 'group.error',
} as const;

export type GroupPacketType = (typeof GroupPacketTypes)[keyof typeof GroupPacketTypes];

export type GroupMemberRank = 'owner' | 'admin' | 'member' | 'requested' | 'invited';
export type GroupAccessMode = 'open' | 'request' | 'invite_only';
export type GroupForumMode = 'open' | 'members_only' | 'admins_only' | 'disabled';

export interface GroupInfo {
  id: number;
  name: string;
  description: string;
  badge: string;
  ownerId: number;
  ownerName: string;
  memberCount: number;
  roomId: number | null;
  roomName: string | null;
  accessMode: GroupAccessMode;
  forumMode: GroupForumMode;
  createdAt: string;
  memberRank: GroupMemberRank | null;
}

export interface GroupMember {
  userId: number;
  username: string;
  figureString: string;
  rank: GroupMemberRank;
  joinedAt: string;
}

export interface GroupThread {
  id: number;
  authorId: number;
  authorName: string;
  subject: string;
  replyCount: number;
  pinned: boolean;
  locked: boolean;
  hidden: boolean;
  createdAt: string;
  lastReplyAt: string;
}

export interface GroupPost {
  id: number;
  threadId: number;
  authorId: number;
  authorName: string;
  authorFigure: string;
  body: string;
  deleted: boolean;
  createdAt: string;
}

export interface GroupCreatePayload {
  name: string;
  description: string;
  badge: string;
  roomId?: number;
  accessMode: GroupAccessMode;
  forumMode: GroupForumMode;
}

export interface GroupJoinPayload {
  groupId: number;
}

export interface GroupForumThreadCreatePayload {
  groupId: number;
  subject: string;
  body: string;
}

export interface GroupForumPostCreatePayload {
  groupId: number;
  threadId: number;
  body: string;
}

export interface GroupSearchPayload {
  query: string;
  page: number;
  pageSize: number;
}

export interface GroupSearchResultPayload {
  groups: GroupInfo[];
  total: number;
  page: number;
}
