export const ErrorCodes = {
  // Auth
  AUTH_INVALID_TICKET: 'auth.invalid_ticket',
  AUTH_TICKET_EXPIRED: 'auth.ticket_expired',
  AUTH_BANNED: 'auth.banned',
  AUTH_ALREADY_CONNECTED: 'auth.already_connected',
  AUTH_INVALID_CREDENTIALS: 'auth.invalid_credentials',
  AUTH_EMAIL_NOT_VERIFIED: 'auth.email_not_verified',
  AUTH_2FA_REQUIRED: 'auth.2fa_required',
  AUTH_2FA_INVALID: 'auth.2fa_invalid',
  AUTH_RATE_LIMITED: 'auth.rate_limited',

  // Room
  ROOM_NOT_FOUND: 'room.not_found',
  ROOM_ACCESS_DENIED: 'room.access_denied',
  ROOM_FULL: 'room.full',
  ROOM_BANNED: 'room.banned',
  ROOM_DOORBELL_DECLINED: 'room.doorbell_declined',
  ROOM_INVALID_MODEL: 'room.invalid_model',
  ROOM_NAME_TAKEN: 'room.name_taken',

  // Furniture
  FURNI_NOT_FOUND: 'furni.not_found',
  FURNI_PLACEMENT_INVALID: 'furni.placement_invalid',
  FURNI_PERMISSION_DENIED: 'furni.permission_denied',
  FURNI_NOT_IN_INVENTORY: 'furni.not_in_inventory',
  FURNI_ALREADY_PLACED: 'furni.already_placed',
  FURNI_STACK_LIMIT: 'furni.stack_limit',

  // Economy
  ECO_INSUFFICIENT_CREDITS: 'eco.insufficient_credits',
  ECO_INSUFFICIENT_DIAMONDS: 'eco.insufficient_diamonds',
  ECO_INSUFFICIENT_NUT_POINTS: 'eco.insufficient_nut_points',
  ECO_IDEMPOTENCY_CONFLICT: 'eco.idempotency_conflict',
  ECO_TRANSACTION_FAILED: 'eco.transaction_failed',

  // Catalogue
  CAT_ITEM_NOT_FOUND: 'cat.item_not_found',
  CAT_ITEM_NOT_AVAILABLE: 'cat.item_not_available',
  CAT_LIMITED_SOLD_OUT: 'cat.limited_sold_out',
  CAT_PURCHASE_LIMIT_REACHED: 'cat.purchase_limit_reached',

  // Trade
  TRADE_NOT_FOUND: 'trade.not_found',
  TRADE_ALREADY_ACCEPTED: 'trade.already_accepted',
  TRADE_ITEM_NOT_OWNED: 'trade.item_not_owned',
  TRADE_PARTNER_OFFLINE: 'trade.partner_offline',
  TRADE_IN_PROGRESS: 'trade.in_progress',
  TRADE_CANCELLED: 'trade.cancelled',

  // Marketplace
  MKT_LISTING_NOT_FOUND: 'mkt.listing_not_found',
  MKT_LISTING_EXPIRED: 'mkt.listing_expired',
  MKT_OWN_LISTING: 'mkt.own_listing',

  // Social
  SOCIAL_ALREADY_FRIENDS: 'social.already_friends',
  SOCIAL_REQUEST_PENDING: 'social.request_pending',
  SOCIAL_BLOCKED: 'social.blocked',
  SOCIAL_USER_NOT_FOUND: 'social.user_not_found',
  SOCIAL_FRIEND_LIMIT: 'social.friend_limit',

  // Group
  GROUP_NOT_FOUND: 'group.not_found',
  GROUP_ALREADY_MEMBER: 'group.already_member',
  GROUP_INVITE_ONLY: 'group.invite_only',
  GROUP_PERMISSION_DENIED: 'group.permission_denied',
  GROUP_LIMIT_REACHED: 'group.limit_reached',

  // Game
  GAME_NOT_FOUND: 'game.not_found',
  GAME_NOT_JOINABLE: 'game.not_joinable',
  GAME_TEAM_FULL: 'game.team_full',
  GAME_ALREADY_IN_MATCH: 'game.already_in_match',

  // Wired
  WIRED_VARIABLE_NOT_FOUND: 'wired.variable_not_found',
  WIRED_INVALID_EXPRESSION: 'wired.invalid_expression',
  WIRED_EXECUTION_LIMIT: 'wired.execution_limit',
  WIRED_CYCLE_DETECTED: 'wired.cycle_detected',

  // Moderation
  MOD_PERMISSION_DENIED: 'mod.permission_denied',
  MOD_TARGET_NOT_FOUND: 'mod.target_not_found',
  MOD_ALREADY_BANNED: 'mod.already_banned',
  MOD_REPORT_DUPLICATE: 'mod.report_duplicate',

  // RP
  RP_CHARACTER_NOT_FOUND: 'rp.character_not_found',
  RP_FACTION_NOT_FOUND: 'rp.faction_not_found',
  RP_VEHICLE_NOT_FOUND: 'rp.vehicle_not_found',
  RP_PROPERTY_NOT_FOUND: 'rp.property_not_found',
  RP_INSUFFICIENT_CASH: 'rp.insufficient_cash',
  RP_LICENCE_REQUIRED: 'rp.licence_required',
  RP_IN_CUSTODY: 'rp.in_custody',

  // Garden
  GARDEN_PLOT_NOT_FOUND: 'garden.plot_not_found',
  GARDEN_PLOT_OCCUPIED: 'garden.plot_occupied',
  GARDEN_PLANT_WITHERED: 'garden.plant_withered',
  GARDEN_SEASON_INACTIVE: 'garden.season_inactive',

  // Generic
  GENERIC_RATE_LIMITED: 'generic.rate_limited',
  GENERIC_INTERNAL_ERROR: 'generic.internal_error',
  GENERIC_NOT_FOUND: 'generic.not_found',
  GENERIC_PERMISSION_DENIED: 'generic.permission_denied',
  GENERIC_INVALID_PAYLOAD: 'generic.invalid_payload',
} as const;

export type ErrorCode = (typeof ErrorCodes)[keyof typeof ErrorCodes];
