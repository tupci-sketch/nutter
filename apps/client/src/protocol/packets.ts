export const Packet = {
  // System
  SYSTEM_PING: 'system.ping',
  SYSTEM_PONG: 'system.pong',

  // Auth
  AUTH_LOGIN: 'auth.login',
  AUTH_LOGIN_RESPONSE: 'auth.login.response',
  AUTH_LOGOUT: 'auth.logout',
  AUTH_WORLD_SWITCH: 'auth.world.switch',
  AUTH_WORLD_SWITCHED: 'auth.world.switched',

  // Room
  ROOM_JOIN: 'room.join',
  ROOM_JOIN_RESULT: 'room.join.result',
  ROOM_LEAVE: 'room.leave',
  ROOM_STATE: 'room.state',
  ROOM_MOVE: 'room.move',
  ROOM_CHAT: 'room.chat',
  ROOM_USER_JOIN: 'room.user.join',
  ROOM_USER_LEAVE: 'room.user.leave',
  ROOM_USER_MOVE: 'room.user.move',
  ROOM_CHAT_BROADCAST: 'room.chat.broadcast',
  ROOM_USER_LIST: 'room.user.list',
  ROOM_INFO: 'room.info',
  ROOM_INFO_RESULT: 'room.info.result',

  // Navigator
  NAV_SEARCH: 'nav.search',
  NAV_SEARCH_RESULT: 'nav.search.result',
  NAV_POPULAR: 'nav.popular',
  NAV_POPULAR_RESULT: 'nav.popular.result',
  NAV_MY_ROOMS: 'nav.my.rooms',
  NAV_MY_ROOMS_RESULT: 'nav.my.rooms.result',

  // Furniture
  FURNI_LIST: 'furni.list',
  FURNI_LIST_RESULT: 'furni.list.result',
  FURNI_PLACE: 'furni.place',
  FURNI_PLACED: 'furni.placed',
  FURNI_MOVE: 'furni.move',
  FURNI_MOVED: 'furni.moved',
  FURNI_ROTATE: 'furni.rotate',
  FURNI_PICKUP: 'furni.pickup',
  FURNI_PICKED_UP: 'furni.picked_up',

  // Economy / Catalogue / Inventory
  CAT_PAGE: 'catalogue.page',
  CAT_PAGE_RESULT: 'catalogue.page.result',
  CAT_PURCHASE: 'catalogue.purchase',
  CAT_PURCHASED: 'catalogue.purchased',
  INV_LIST: 'inventory.list',
  INV_LIST_RESULT: 'inventory.list.result',
  WALLET_BALANCE: 'wallet.balance',
  WALLET_BALANCE_RESULT: 'wallet.balance.result',

  // Trade
  TRADE_INIT: 'trade.init',
  TRADE_OFFER: 'trade.offer',
  TRADE_CONFIRM: 'trade.confirm',
  TRADE_ACCEPT: 'trade.accept',
  TRADE_CANCEL: 'trade.cancel',
  TRADE_STATE: 'trade.state',

  // Marketplace
  MKT_LIST: 'marketplace.list',
  MKT_LIST_RESULT: 'marketplace.list.result',
  MKT_BUY: 'marketplace.buy',
  MKT_BOUGHT: 'marketplace.bought',
  MKT_LIST_ITEM: 'marketplace.list.item',
  MKT_LISTED: 'marketplace.listed',

  // Social
  FRIEND_LIST: 'friend.list',
  FRIEND_LIST_RESULT: 'friend.list.result',
  FRIEND_REQUEST: 'friend.request',
  FRIEND_ACCEPT: 'friend.accept',
  FRIEND_DECLINE: 'friend.decline',
  FRIEND_REMOVE: 'friend.remove',
  MSG_SEND: 'message.send',
  MSG_RECEIVE: 'message.receive',
  MSG_INBOX: 'message.inbox',
  MSG_INBOX_RESULT: 'message.inbox.result',
  GROUP_LIST: 'group.list',
  GROUP_LIST_RESULT: 'group.list.result',
  GROUP_JOIN: 'group.join',
  GROUP_LEAVE: 'group.leave',

  // Progression
  PROFILE: 'profile.get',
  PROFILE_RESULT: 'profile.get.result',
  ACH_LIST: 'achievement.list',
  ACH_LIST_RESULT: 'achievement.list.result',
  QUEST_LIST: 'quest.list',
  QUEST_LIST_RESULT: 'quest.list.result',
  BADGE_LIST: 'badge.list',
  BADGE_LIST_RESULT: 'badge.list.result',

  // Game
  GAME_LIST: 'game.list',
  GAME_LIST_RESULT: 'game.list.result',
  GAME_JOIN: 'game.join',
  GAME_LEAVE: 'game.leave',
  GAME_STATE: 'game.state',
  GAME_INPUT: 'game.input',

  // Garden
  GARDEN_STATE: 'garden.state',
  GARDEN_STATE_RESULT: 'garden.state.result',
  GARDEN_PLANT: 'garden.plant',
  GARDEN_WATER: 'garden.water',
  GARDEN_HARVEST: 'garden.harvest',
  GARDEN_PLANTED: 'garden.planted',
  GARDEN_WATERED: 'garden.watered',
  GARDEN_HARVESTED: 'garden.harvested',
  GARDEN_GOAL_UPDATED: 'garden.goal.updated',

  // RP
  RP_CHARACTER_CREATE: 'rp.character.create',
  RP_CHARACTER_CREATED: 'rp.character.created',
  RP_CHARACTER_INFO: 'rp.character.info',
  RP_CHARACTER_INFO_RESULT: 'rp.character.info.result',
  RP_FACTION_LIST: 'rp.faction.list',
  RP_FACTION_LIST_RESULT: 'rp.faction.list.result',
  RP_JOB_LIST: 'rp.job.list',
  RP_JOB_LIST_RESULT: 'rp.job.list.result',
  RP_BANK_BALANCE: 'rp.bank.balance',
  RP_BANK_BALANCE_RESULT: 'rp.bank.balance.result',
  RP_BANK_DEPOSIT: 'rp.bank.deposit',
  RP_BANK_WITHDRAW: 'rp.bank.withdraw',
  RP_BANK_TRANSFER: 'rp.bank.transfer',
  RP_BANK_TRANSACTION: 'rp.bank.transaction',
  RP_DISPATCH_CREATE: 'rp.dispatch.create',
  RP_DISPATCH_LIST: 'rp.dispatch.list',
  RP_DISPATCH_LIST_RESULT: 'rp.dispatch.list.result',
  RP_ELECTION_LIST: 'rp.election.list',
  RP_ELECTION_LIST_RESULT: 'rp.election.list.result',
} as const;

export type PacketType = typeof Packet[keyof typeof Packet];

export interface Envelope {
  type: PacketType | string;
  payload: unknown;
}
