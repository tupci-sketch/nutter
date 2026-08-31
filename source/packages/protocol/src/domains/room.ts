export const RoomPacketTypes = {
  // c2s navigation
  NAV_SEARCH: 'room.nav.search',
  NAV_CATEGORIES: 'room.nav.categories',
  NAV_MY_ROOMS: 'room.nav.my_rooms',
  NAV_POPULAR: 'room.nav.popular',
  NAV_FAVORITES: 'room.nav.favorites',

  // c2s entry
  ENTER: 'room.enter',
  LEAVE: 'room.leave',
  DOORBELL_RESPOND: 'room.doorbell.respond',

  // c2s movement
  MOVE: 'room.move',
  LOOK: 'room.look',

  // c2s chat
  CHAT: 'room.chat',
  SHOUT: 'room.shout',
  WHISPER: 'room.whisper',

  // c2s management
  CREATE: 'room.create',
  UPDATE_SETTINGS: 'room.settings.update',
  DELETE: 'room.delete',
  BAN_USER: 'room.user.ban',
  KICK_USER: 'room.user.kick',
  MUTE_USER: 'room.user.mute',
  GRANT_RIGHTS: 'room.rights.grant',
  REVOKE_RIGHTS: 'room.rights.revoke',
  RATE: 'room.rate',
  FAVORITE_ADD: 'room.favorite.add',
  FAVORITE_REMOVE: 'room.favorite.remove',

  // s2c
  NAV_SEARCH_RESULT: 'room.nav.search.result',
  NAV_CATEGORIES_RESULT: 'room.nav.categories.result',
  NAV_MY_ROOMS_RESULT: 'room.nav.my_rooms.result',
  NAV_POPULAR_RESULT: 'room.nav.popular.result',
  NAV_FAVORITES_RESULT: 'room.nav.favorites.result',

  ENTER_SUCCESS: 'room.enter.success',
  ENTER_ERROR: 'room.enter.error',
  DOORBELL_RING: 'room.doorbell.ring',
  DOORBELL_RESPONSE: 'room.doorbell.response',

  STATE: 'room.state',
  USER_ENTERED: 'room.user.entered',
  USER_LEFT: 'room.user.left',
  USER_MOVED: 'room.user.moved',
  USER_LOOKED: 'room.user.looked',
  USER_CHAT: 'room.user.chat',
  USER_SHOUT: 'room.user.shout',
  USER_WHISPER: 'room.user.whisper',
  USER_TYPING: 'room.user.typing',
  USER_KICKED: 'room.user.kicked',
  USER_BANNED: 'room.user.banned',
  USER_MUTED: 'room.user.muted',

  SETTINGS_UPDATED: 'room.settings.updated',
  RIGHTS_GRANTED: 'room.rights.granted',
  RIGHTS_REVOKED: 'room.rights.revoked',
  RATED: 'room.rated',
} as const;

export type RoomPacketType = (typeof RoomPacketTypes)[keyof typeof RoomPacketTypes];

export interface RoomEnterPayload {
  roomId: number;
  password?: string;
}

export interface RoomMovePayload {
  x: number;
  y: number;
}

export interface RoomLookPayload {
  direction: number;
  headDirection: number;
}

export interface RoomChatPayload {
  message: string;
  styleId: number;
}

export interface RoomWhisperPayload {
  targetUsername: string;
  message: string;
  styleId: number;
}

export interface RoomUserEntry {
  userId: number;
  username: string;
  figureString: string;
  x: number;
  y: number;
  z: number;
  direction: number;
  headDirection: number;
  danceId: number;
  carryItemId: number | null;
  effects: string[];
  motto: string;
  rank: number;
  buildersClub: boolean;
}

export interface RoomEnterSuccessPayload {
  roomId: number;
  roomName: string;
  ownerId: number;
  ownerName: string;
  users: RoomUserEntry[];
  hasRights: boolean;
  isOwner: boolean;
}

export interface RoomStatePayload {
  roomId: number;
  furniData: unknown[];
  wallData: unknown[];
}

export interface RoomCreatePayload {
  name: string;
  description: string;
  modelId: string;
  categoryId: number;
  maxUsers: number;
  tradeMode: 0 | 1 | 2;
  accessMode: 0 | 1 | 2;
  password?: string;
  tags: string[];
}

export interface RoomSettingsUpdatePayload {
  name?: string;
  description?: string;
  accessMode?: 0 | 1 | 2;
  password?: string;
  maxUsers?: number;
  tradeMode?: 0 | 1 | 2;
  allowPets?: boolean;
  allowPetsEat?: boolean;
  blockRoomWalk?: boolean;
  allowWalkthrough?: boolean;
  tags?: string[];
}

export interface NavSearchPayload {
  query: string;
  categoryId?: number;
  filter?: 'top_rated' | 'new' | 'popular';
  page: number;
  pageSize: number;
}

export interface NavRoomEntry {
  roomId: number;
  name: string;
  description: string;
  ownerId: number;
  ownerName: string;
  userCount: number;
  maxUsers: number;
  categoryId: number;
  rating: number;
  accessMode: 0 | 1 | 2;
  hasPassword: boolean;
  thumbnailUrl: string | null;
  tags: string[];
}

export interface NavSearchResultPayload {
  query: string;
  rooms: NavRoomEntry[];
  total: number;
  page: number;
  pageSize: number;
}
