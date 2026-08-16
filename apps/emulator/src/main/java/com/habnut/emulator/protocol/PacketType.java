package com.habnut.emulator.protocol;

public final class PacketType {
    private PacketType() {}

    // Auth
    public static final String AUTH_LOGIN = "auth.login";
    public static final String AUTH_LOGOUT = "auth.logout";
    public static final String AUTH_WORLD_SWITCH = "auth.world.switch";
    public static final String AUTH_PING = "auth.ping";
    public static final String AUTH_LOGIN_SUCCESS = "auth.login.success";
    public static final String AUTH_LOGIN_ERROR = "auth.login.error";
    public static final String AUTH_LOGOUT_SUCCESS = "auth.logout.success";
    public static final String AUTH_WORLD_SWITCHED = "auth.world.switched";
    public static final String AUTH_WORLD_SWITCH_ERROR = "auth.world.switch.error";
    public static final String AUTH_PONG = "auth.pong";
    public static final String AUTH_SESSION_EXPIRED = "auth.session.expired";
    public static final String AUTH_KICKED = "auth.kicked";

    // Room navigation
    public static final String ROOM_NAV_SEARCH = "room.nav.search";
    public static final String ROOM_NAV_CATEGORIES = "room.nav.categories";
    public static final String ROOM_NAV_MY_ROOMS = "room.nav.my_rooms";
    public static final String ROOM_NAV_POPULAR = "room.nav.popular";
    public static final String ROOM_NAV_FAVORITES = "room.nav.favorites";
    public static final String ROOM_ENTER = "room.enter";
    public static final String ROOM_LEAVE = "room.leave";
    public static final String ROOM_DOORBELL_RESPOND = "room.doorbell.respond";
    public static final String ROOM_MOVE = "room.move";
    public static final String ROOM_LOOK = "room.look";
    public static final String ROOM_CHAT = "room.chat";
    public static final String ROOM_SHOUT = "room.shout";
    public static final String ROOM_WHISPER = "room.whisper";
    public static final String ROOM_CREATE = "room.create";
    public static final String ROOM_UPDATE_SETTINGS = "room.settings.update";
    public static final String ROOM_DELETE = "room.delete";
    public static final String ROOM_BAN_USER = "room.user.ban";
    public static final String ROOM_KICK_USER = "room.user.kick";
    public static final String ROOM_MUTE_USER = "room.user.mute";
    public static final String ROOM_GRANT_RIGHTS = "room.rights.grant";
    public static final String ROOM_REVOKE_RIGHTS = "room.rights.revoke";
    public static final String ROOM_RATE = "room.rate";
    // Room decoration: the three surfaces an owner can change, plus the wall
    // and floor thickness settings that accompany them.
    public static final String ROOM_DECORATION_UPDATE = "room.decoration.update";
    public static final String ROOM_DECORATION_UPDATED = "room.decoration.updated";
    // Avatar expression, visible to every occupant of the room.
    public static final String ROOM_USER_DANCE = "room.user.dance";
    public static final String ROOM_USER_DANCED = "room.user.danced";
    public static final String ROOM_USER_EFFECT = "room.user.effect";
    public static final String ROOM_USER_EFFECT_SET = "room.user.effect.set";
    public static final String ROOM_USER_EFFECT_LIST = "room.user.effect.list";
    public static final String ROOM_USER_EFFECT_LIST_RESULT = "room.user.effect.list.result";
    public static final String ROOM_USER_SIGN = "room.user.sign";
    public static final String ROOM_USER_SIGNED = "room.user.signed";
    public static final String ROOM_USER_HAND_ITEM = "room.user.hand_item";
    public static final String ROOM_FAVORITE_ADD = "room.favorite.add";
    public static final String ROOM_FAVORITE_REMOVE = "room.favorite.remove";
    public static final String ROOM_ENTER_SUCCESS = "room.enter.success";
    public static final String ROOM_ENTER_ERROR = "room.enter.error";
    public static final String ROOM_DOORBELL_RING = "room.doorbell.ring";
    public static final String ROOM_STATE = "room.state";
    public static final String ROOM_USER_ENTERED = "room.user.entered";
    public static final String ROOM_USER_LEFT = "room.user.left";
    public static final String ROOM_USER_MOVED = "room.user.moved";
    public static final String ROOM_USER_CHAT = "room.user.chat";
    public static final String ROOM_USER_SHOUT = "room.user.shout";
    public static final String ROOM_USER_WHISPER = "room.user.whisper";
    public static final String ROOM_USER_TYPING = "room.user.typing";
    public static final String ROOM_USER_KICKED = "room.user.kicked";
    public static final String ROOM_USER_BANNED = "room.user.banned";
    public static final String ROOM_USER_MUTED = "room.user.muted";
    public static final String ROOM_NAV_SEARCH_RESULT = "room.nav.search.result";
    public static final String ROOM_NAV_POPULAR_RESULT = "room.nav.popular.result";
    public static final String ROOM_NAV_MY_ROOMS_RESULT = "room.nav.my_rooms.result";
    public static final String ROOM_NAV_FAVORITES_RESULT = "room.nav.favorites.result";
    public static final String ROOM_CREATED = "room.created";

    // Furniture
    public static final String FURNI_PLACE = "furni.place";
    public static final String FURNI_MOVE = "furni.move";
    public static final String FURNI_ROTATE = "furni.rotate";
    public static final String FURNI_PICKUP = "furni.pickup";
    public static final String FURNI_INTERACT = "furni.interact";
    public static final String FURNI_USE = "furni.use";
    public static final String FURNI_WALL_PLACE = "furni.wall.place";
    public static final String FURNI_WALL_MOVE = "furni.wall.move";
    public static final String FURNI_WALL_PICKUP = "furni.wall.pickup";
    public static final String FURNI_DICE_ROLL = "furni.dice.roll";
    public static final String FURNI_TELEPORT_LINK = "furni.teleport.link";
    public static final String FURNI_TELEPORT_USE = "furni.teleport.use";
    public static final String FURNI_PLACED = "furni.placed";
    public static final String FURNI_MOVED = "furni.moved";
    public static final String FURNI_ROTATED = "furni.rotated";
    public static final String FURNI_PICKED_UP = "furni.picked_up";
    public static final String FURNI_INTERACTED = "furni.interacted";
    public static final String FURNI_WALL_PLACED = "furni.wall.placed";
    public static final String FURNI_WALL_MOVED = "furni.wall.moved";
    public static final String FURNI_WALL_PICKED_UP = "furni.wall.picked_up";
    public static final String FURNI_ROLLER_MOVE = "furni.roller.move";
    public static final String FURNI_STATE_CHANGED = "furni.state.changed";
    public static final String FURNI_DICE_RESULT = "furni.dice.result";
    public static final String FURNI_PLACE_ERROR = "furni.place.error";

    // Inventory
    public static final String INVENTORY_LIST = "inventory.list";
    public static final String INVENTORY_SEARCH = "inventory.search";
    public static final String INVENTORY_BADGES_LIST = "inventory.badges.list";
    public static final String INVENTORY_BADGE_EQUIP = "inventory.badge.equip";
    public static final String INVENTORY_BADGE_UNEQUIP = "inventory.badge.unequip";
    public static final String INVENTORY_LIST_RESULT = "inventory.list.result";
    public static final String INVENTORY_ITEM_ADDED = "inventory.item.added";
    public static final String INVENTORY_ITEM_REMOVED = "inventory.item.removed";
    public static final String INVENTORY_BADGES_LIST_RESULT = "inventory.badges.list.result";
    public static final String INVENTORY_BADGE_EQUIPPED = "inventory.badge.equipped";

    // Catalogue
    public static final String CAT_PAGES = "catalogue.pages";
    public static final String CAT_PAGE = "catalogue.page";
    public static final String CAT_PURCHASE = "catalogue.purchase";
    public static final String CAT_GIFT = "catalogue.gift";
    public static final String CAT_REDEEM_VOUCHER = "catalogue.voucher.redeem";
    public static final String CAT_PAGES_RESULT = "catalogue.pages.result";
    public static final String CAT_PAGE_RESULT = "catalogue.page.result";
    public static final String CAT_PURCHASE_SUCCESS = "catalogue.purchase.success";
    public static final String CAT_PURCHASE_ERROR = "catalogue.purchase.error";
    public static final String CAT_GIFT_SENT = "catalogue.gift.sent";
    public static final String CAT_VOUCHER_REDEEMED = "catalogue.voucher.redeemed";
    public static final String CAT_VOUCHER_ERROR = "catalogue.voucher.error";
    public static final String CAT_PRESENT_OPENED = "catalogue.present.opened";

    // Trade
    public static final String TRADE_OPEN = "trade.open";
    public static final String TRADE_OFFER_ADD = "trade.offer.add";
    public static final String TRADE_OFFER_REMOVE = "trade.offer.remove";
    public static final String TRADE_CONFIRM = "trade.confirm";
    public static final String TRADE_ACCEPT = "trade.accept";
    public static final String TRADE_UNACCEPT = "trade.unaccept";
    public static final String TRADE_CANCEL = "trade.cancel";
    public static final String TRADE_OPENED = "trade.opened";
    public static final String TRADE_OFFER_UPDATED = "trade.offer.updated";
    public static final String TRADE_CONFIRMED = "trade.confirmed";
    public static final String TRADE_ACCEPTED = "trade.accepted";
    public static final String TRADE_COMPLETED = "trade.completed";
    public static final String TRADE_CANCELLED = "trade.cancelled";
    public static final String TRADE_ERROR = "trade.error";

    // Marketplace
    public static final String MKT_SEARCH = "marketplace.search";
    public static final String MKT_LISTING_CREATE = "marketplace.listing.create";
    public static final String MKT_LISTING_CANCEL = "marketplace.listing.cancel";
    public static final String MKT_LISTING_BUY = "marketplace.listing.buy";
    public static final String MKT_MY_LISTINGS = "marketplace.listings.mine";
    public static final String MKT_SEARCH_RESULT = "marketplace.search.result";
    public static final String MKT_MY_LISTINGS_RESULT = "marketplace.listings.mine.result";
    public static final String MKT_LISTING_CREATED = "marketplace.listing.created";
    public static final String MKT_LISTING_SOLD = "marketplace.listing.sold";
    public static final String MKT_LISTING_BOUGHT = "marketplace.listing.bought";
    public static final String MKT_LISTING_EXPIRED = "marketplace.listing.expired";

    // Social
    public static final String SOCIAL_FRIEND_LIST = "social.friend.list";
    public static final String SOCIAL_FRIEND_REQUEST_SEND = "social.friend.request.send";
    public static final String SOCIAL_FRIEND_REQUEST_ACCEPT = "social.friend.request.accept";
    public static final String SOCIAL_FRIEND_REQUEST_DECLINE = "social.friend.request.decline";
    public static final String SOCIAL_FRIEND_REMOVE = "social.friend.remove";
    public static final String SOCIAL_BLOCK = "social.block";
    public static final String SOCIAL_MSG_SEND = "social.msg.send";
    public static final String SOCIAL_MSG_LIST = "social.msg.list";
    public static final String SOCIAL_FRIEND_LIST_RESULT = "social.friend.list.result";
    public static final String SOCIAL_FRIEND_ONLINE = "social.friend.online";
    public static final String SOCIAL_FRIEND_OFFLINE = "social.friend.offline";
    public static final String SOCIAL_FRIEND_ADDED = "social.friend.added";
    public static final String SOCIAL_FRIEND_REMOVED = "social.friend.removed";
    public static final String SOCIAL_FRIEND_REQUEST_RECEIVED = "social.friend.request.received";
    public static final String SOCIAL_MSG_RECEIVED = "social.msg.received";

    // Groups
    public static final String GROUP_CREATE = "group.create";
    public static final String GROUP_JOIN = "group.join";
    public static final String GROUP_LEAVE = "group.leave";
    public static final String GROUP_INFO = "group.info";
    public static final String GROUP_SEARCH = "group.search";
    public static final String GROUP_FORUM_THREAD_CREATE = "group.forum.thread.create";
    public static final String GROUP_FORUM_POST_CREATE = "group.forum.post.create";
    public static final String GROUP_CREATED = "group.created";
    public static final String GROUP_JOINED = "group.joined";
    public static final String GROUP_LEFT = "group.left";
    public static final String GROUP_INFO_RESULT = "group.info.result";
    public static final String GROUP_INVITED = "group.invited";

    // Profile
    public static final String PROFILE_VIEW = "profile.view";
    public static final String PROFILE_UPDATE_MOTTO = "profile.motto.update";
    public static final String PROFILE_UPDATE_FIGURE = "profile.figure.update";
    public static final String PROFILE_VIEW_RESULT = "profile.view.result";
    public static final String PROFILE_MOTTO_UPDATED = "profile.motto.updated";
    public static final String PROFILE_FIGURE_UPDATED = "profile.figure.updated";

    // Game
    public static final String GAME_JOIN = "game.join";
    public static final String GAME_LEAVE = "game.leave";
    public static final String GAME_READY = "game.ready";
    public static final String GAME_INPUT = "game.input";
    public static final String GAME_FOOTBALL_KICK = "game.football.kick";
    public static final String GAME_BATTLEBALL_JUMP = "game.battleball.jump";
    public static final String GAME_FREEZE_THROW = "game.freeze.throw";
    public static final String GAME_STARTED = "game.started";
    public static final String GAME_ENDED = "game.ended";
    public static final String GAME_TICK = "game.tick";
    public static final String GAME_SCORE_UPDATE = "game.score.update";
    public static final String GAME_LOBBY_STATE = "game.lobby.state";
    public static final String GAME_COUNTDOWN = "game.countdown";
    public static final String GAME_FOOTBALL_STATE = "game.football.state";
    public static final String GAME_FOOTBALL_GOAL = "game.football.goal";
    public static final String GAME_FOOTBALL_BALL_MOVED = "game.football.ball.moved";
    public static final String GAME_BATTLEBALL_STATE = "game.battleball.state";
    public static final String GAME_BATTLEBALL_TILE_CLAIMED = "game.battleball.tile.claimed";
    public static final String GAME_FREEZE_STATE = "game.freeze.state";
    public static final String GAME_FREEZE_PLAYER_FROZEN = "game.freeze.player.frozen";
    public static final String GAME_LEADERBOARD    = "game.leaderboard";
    public static final String GAME_STATE_CHANGE   = "game.state.change";
    public static final String GAME_EVENT          = "game.event";
    public static final String GAME_END            = "game.end";
    public static final String GAME_MATCHMAKING_QUEUED  = "game.matchmaking.queued";
    public static final String GAME_MATCHMAKING_MATCHED = "game.matchmaking.matched";
    public static final String GAME_MATCHMAKING_CANCEL  = "game.matchmaking.cancel";

    // Tournament
    public static final String TRN_LIST = "tournament.list";
    public static final String TRN_INFO = "tournament.info";
    public static final String TRN_REGISTER = "tournament.register";
    public static final String TRN_BRACKET = "tournament.bracket";
    public static final String TRN_LIST_RESULT = "tournament.list.result";
    public static final String TRN_REGISTERED = "tournament.registered";
    public static final String TRN_BRACKET_RESULT = "tournament.bracket.result";
    public static final String TRN_MATCH_STARTED = "tournament.match.started";
    public static final String TRN_MATCH_ENDED = "tournament.match.ended";
    public static final String TRN_CHAMPION = "tournament.champion";

    // Wired
    public static final String WIRED_TRIGGER_OPEN = "wired.trigger.open";
    public static final String WIRED_TRIGGER_SAVE = "wired.trigger.save";
    public static final String WIRED_ACTION_OPEN = "wired.action.open";
    public static final String WIRED_ACTION_SAVE = "wired.action.save";
    public static final String WIRED_CONDITION_OPEN = "wired.condition.open";
    public static final String WIRED_CONDITION_SAVE = "wired.condition.save";
    public static final String WIRED_SELECTOR_OPEN = "wired.selector.open";
    public static final String WIRED_SELECTOR_SAVE = "wired.selector.save";
    public static final String WIRED_VARIABLE_GET = "wired.variable.get";
    public static final String WIRED_VARIABLE_SET = "wired.variable.set";
    public static final String WIRED_VARIABLE_LIST = "wired.variable.list";
    public static final String WIRED_DEBUG_START = "wired.debug.start";
    public static final String WIRED_DEBUG_STOP = "wired.debug.stop";
    public static final String WIRED_SIGNAL_SEND = "wired.signal.send";
    public static final String WIRED_STACK_EXPORT = "wired.stack.export";
    public static final String WIRED_STACK_IMPORT = "wired.stack.import";
    public static final String WIRED_TRIGGER_SAVED = "wired.trigger.saved";
    public static final String WIRED_ACTION_SAVED = "wired.action.saved";
    public static final String WIRED_CONDITION_SAVED = "wired.condition.saved";
    public static final String WIRED_SELECTOR_SAVED = "wired.selector.saved";
    public static final String WIRED_VARIABLE_RESULT = "wired.variable.result";
    public static final String WIRED_VARIABLE_LIST_RESULT = "wired.variable.list.result";
    public static final String WIRED_VARIABLE_CHANGED = "wired.variable.changed";
    public static final String WIRED_DEBUG_EVENT = "wired.debug.event";
    public static final String WIRED_SIGNAL_RECEIVED = "wired.signal.received";
    public static final String WIRED_EXECUTION_LOG = "wired.execution.log";

    // Pet
    public static final String PET_PLACE = "pet.place";
    public static final String PET_PICKUP = "pet.pickup";
    public static final String PET_COMMAND = "pet.command";
    public static final String PET_FEED = "pet.feed";
    public static final String PET_PLACED = "pet.placed";
    public static final String PET_PICKED_UP = "pet.picked_up";
    public static final String PET_MOVED = "pet.moved";
    public static final String PET_STATE_CHANGED = "pet.state.changed";
    public static final String PET_STAT_UPDATED = "pet.stat.updated";
    public static final String PET_LEVELED_UP = "pet.leveled_up";

    // Bot
    public static final String BOT_PLACE = "bot.place";
    public static final String BOT_PICKUP = "bot.pickup";
    public static final String BOT_UPDATE = "bot.update";
    public static final String BOT_COMMAND = "bot.command";
    public static final String BOT_PLACED = "bot.placed";
    public static final String BOT_PICKED_UP = "bot.picked_up";
    public static final String BOT_MOVED = "bot.moved";
    public static final String BOT_CHAT = "bot.chat";

    // Achievement
    public static final String ACH_LIST = "achievement.list";
    public static final String ACH_LIST_RESULT = "achievement.list.result";
    public static final String ACH_UNLOCKED = "achievement.unlocked";
    public static final String ACH_SCORE_UPDATED = "achievement.score.updated";

    // Quest
    public static final String QUEST_LIST = "quest.list";
    public static final String QUEST_ACCEPT = "quest.accept";
    public static final String QUEST_ABANDON = "quest.abandon";
    public static final String QUEST_LIST_RESULT = "quest.list.result";
    public static final String QUEST_PROGRESS_UPDATED = "quest.progress.updated";
    public static final String QUEST_COMPLETED = "quest.completed";

    // Garden
    public static final String GARDEN_STATE = "garden.state";
    public static final String GARDEN_PLANT = "garden.plant";
    public static final String GARDEN_WATER = "garden.water";
    public static final String GARDEN_HARVEST = "garden.harvest";
    public static final String GARDEN_STATE_RESULT = "garden.state.result";
    public static final String GARDEN_PLANTED = "garden.planted";
    public static final String GARDEN_WATERED = "garden.watered";
    public static final String GARDEN_HARVESTED = "garden.harvested";
    public static final String GARDEN_WITHERED = "garden.withered";
    public static final String GARDEN_GOAL_UPDATED = "garden.goal.updated";
    public static final String GARDEN_GOAL_COMPLETED = "garden.goal.completed";
    public static final String GARDEN_SEASON_CHANGED = "garden.season.changed";

    // Roleplay — characters
    public static final String RP_CHARACTER_CREATE        = "rp.character.create";
    public static final String RP_CHARACTER_CREATED       = "rp.character.created";
    public static final String RP_CHARACTER_INFO          = "rp.character.info";
    public static final String RP_CHARACTER_INFO_RESULT   = "rp.character.info.result";
    public static final String RP_CHARACTER_UPDATE        = "rp.character.update";
    public static final String RP_CHARACTER_UPDATED       = "rp.character.updated";
    // Roleplay — factions
    public static final String RP_FACTION_LIST            = "rp.faction.list";
    public static final String RP_FACTION_LIST_RESULT     = "rp.faction.list.result";
    public static final String RP_FACTION_JOIN            = "rp.faction.join";
    public static final String RP_FACTION_JOINED          = "rp.faction.joined";
    public static final String RP_FACTION_LEAVE           = "rp.faction.leave";
    public static final String RP_FACTION_LEFT            = "rp.faction.left";
    // Roleplay — jobs
    public static final String RP_JOB_LIST                = "rp.job.list";
    public static final String RP_JOB_LIST_RESULT         = "rp.job.list.result";
    public static final String RP_JOB_APPLY               = "rp.job.apply";
    public static final String RP_JOB_APPLIED             = "rp.job.applied";
    public static final String RP_JOB_CLOCKIN             = "rp.job.clockin";
    public static final String RP_JOB_CLOCKOUT            = "rp.job.clockout";
    public static final String RP_JOB_SHIFT_RESULT        = "rp.job.shift.result";
    public static final String RP_PAYROLL_PAID            = "rp.payroll.paid";
    // Roleplay — bank (RP-isolated)
    public static final String RP_BANK_BALANCE            = "rp.bank.balance";
    public static final String RP_BANK_BALANCE_RESULT     = "rp.bank.balance.result";
    public static final String RP_BANK_DEPOSIT            = "rp.bank.deposit";
    public static final String RP_BANK_WITHDRAW           = "rp.bank.withdraw";
    public static final String RP_BANK_TRANSFER           = "rp.bank.transfer";
    public static final String RP_BANK_TRANSACTION        = "rp.bank.transaction";
    // Roleplay — crimes / arrests
    public static final String RP_CRIME_LIST              = "rp.crime.list";
    public static final String RP_CRIME_LIST_RESULT       = "rp.crime.list.result";
    public static final String RP_ARREST                  = "rp.arrest";
    public static final String RP_ARRESTED                = "rp.arrested";
    // Roleplay — court / prison
    public static final String RP_COURT_CASE_LIST         = "rp.court.case.list";
    public static final String RP_COURT_CASE_LIST_RESULT  = "rp.court.case.list.result";
    public static final String RP_COURT_VERDICT           = "rp.court.verdict";
    public static final String RP_SENTENCED               = "rp.sentenced";
    public static final String RP_PRISON_STATUS           = "rp.prison.status";
    public static final String RP_PRISON_STATUS_RESULT    = "rp.prison.status.result";
    public static final String RP_RELEASED                = "rp.released";
    // Roleplay — dispatch
    public static final String RP_DISPATCH_CREATE         = "rp.dispatch.create";
    public static final String RP_DISPATCH_CALL_RECEIVED  = "rp.dispatch.call.received";
    public static final String RP_DISPATCH_LIST           = "rp.dispatch.list";
    public static final String RP_DISPATCH_LIST_RESULT    = "rp.dispatch.list.result";
    public static final String RP_DISPATCH_ACCEPT         = "rp.dispatch.accept";
    public static final String RP_DISPATCH_RESOLVE        = "rp.dispatch.resolve";
    // Roleplay — medical
    public static final String RP_MEDICAL_RECORDS         = "rp.medical.records";
    public static final String RP_MEDICAL_RECORDS_RESULT  = "rp.medical.records.result";
    public static final String RP_MEDICAL_ADMIT           = "rp.medical.admit";
    public static final String RP_MEDICAL_TREAT           = "rp.medical.treat";
    public static final String RP_MEDICAL_DISCHARGE       = "rp.medical.discharge";
    // Roleplay — properties
    public static final String RP_PROPERTY_LIST           = "rp.property.list";
    public static final String RP_PROPERTY_LIST_RESULT    = "rp.property.list.result";
    public static final String RP_PROPERTY_BUY            = "rp.property.buy";
    public static final String RP_PROPERTY_BOUGHT         = "rp.property.bought";
    // Roleplay — government / elections
    public static final String RP_ELECTION_LIST           = "rp.election.list";
    public static final String RP_ELECTION_LIST_RESULT    = "rp.election.list.result";
    public static final String RP_ELECTION_NOMINATE       = "rp.election.nominate";
    public static final String RP_ELECTION_VOTE           = "rp.election.vote";
    public static final String RP_ELECTION_VOTED          = "rp.election.voted";
    public static final String RP_ELECTION_RESULT         = "rp.election.result";
    public static final String RP_GOVT_OFFICES            = "rp.govt.offices";
    public static final String RP_GOVT_OFFICES_RESULT     = "rp.govt.offices.result";
    public static final String RP_GOVT_DECREE             = "rp.govt.decree";
    public static final String RP_GOVT_DECREED            = "rp.govt.decreed";
    public static final String RP_LAW_LIST                = "rp.law.list";
    public static final String RP_LAW_LIST_RESULT         = "rp.law.list.result";
    // Roleplay — scenes
    public static final String RP_SCENE_START             = "rp.scene.start";
    public static final String RP_SCENE_STARTED           = "rp.scene.started";
    public static final String RP_SCENE_END               = "rp.scene.end";
    public static final String RP_SCENE_ENDED             = "rp.scene.ended";
    // Roleplay — crafting
    public static final String RP_CRAFT                   = "rp.craft";
    public static final String RP_CRAFTED                 = "rp.crafted";
    public static final String RP_RECIPE_LIST             = "rp.recipe.list";
    public static final String RP_RECIPE_LIST_RESULT      = "rp.recipe.list.result";

    // Moderation
    public static final String MOD_REPORT_CREATE = "mod.report.create";
    public static final String MOD_REPORT_LIST = "mod.report.list";
    public static final String MOD_REPORT_CLAIM = "mod.report.claim";
    public static final String MOD_REPORT_RESOLVE = "mod.report.resolve";
    public static final String MOD_USER_MUTE = "mod.user.mute";
    public static final String MOD_USER_KICK = "mod.user.kick";
    public static final String MOD_USER_BAN = "mod.user.ban";
    public static final String MOD_USER_UNBAN = "mod.user.unban";
    public static final String MOD_USER_WARN = "mod.user.warn";
    public static final String MOD_CHAT_LOGS = "mod.chat.logs";
    public static final String MOD_USER_INFO = "mod.user.info";
    public static final String MOD_REPORT_ALERT = "mod.report.alert";
    public static final String MOD_CALL_FOR_HELP = "mod.call_for_help";
    public static final String MOD_REPORT_CREATED = "mod.report.created";
    public static final String MOD_USER_MUTED = "mod.user.muted";
    public static final String MOD_USER_KICKED = "mod.user.kicked";
    public static final String MOD_USER_BANNED = "mod.user.banned";
    public static final String MOD_USER_WARNED = "mod.user.warned";
    public static final String MOD_USER_INFO_RESULT = "mod.user.info.result";
    public static final String MOD_CHAT_LOGS_RESULT = "mod.chat.logs.result";

    // Camera / Photos
    public static final String CAM_TAKE     = "camera.take";
    public static final String CAM_PURCHASE = "camera.purchase";
    public static final String CAM_PREVIEW  = "camera.preview";
    public static final String CAM_LIST     = "camera.list";
    public static final String CAM_RESULT   = "camera.result";
    public static final String CAM_PHOTO_LIST_RESULT = "camera.photo.list.result";
    public static final String CAM_PHOTO_DELETED     = "camera.photo.deleted";
    public static final String CAM_DELETE   = "camera.delete";

    // Sound
    public static final String SND_PLAYLIST_GET    = "sound.playlist.get";
    public static final String SND_PLAYLIST_SET    = "sound.playlist.set";
    public static final String SND_TRACK_ADD       = "sound.track.add";
    public static final String SND_TRACK_REMOVE    = "sound.track.remove";
    public static final String SND_TRACK_REORDER   = "sound.track.reorder";
    public static final String SND_PLAYLIST_RESULT = "sound.playlist.result";
    public static final String SND_NOW_PLAYING     = "sound.now_playing";

    // Staff
    public static final String STAFF_COMMAND = "staff.command";
    public static final String STAFF_OVERLAY_REQUEST = "staff.overlay.request";
    public static final String STAFF_HA = "staff.ha";
    public static final String STAFF_ALERT = "staff.alert";
    public static final String STAFF_TELEPORT = "staff.teleport";
    public static final String STAFF_SUMMON = "staff.summon";
    public static final String STAFF_FEATURE_FLAG_SET = "staff.feature_flag.set";
    public static final String STAFF_SYSTEM_MESSAGE = "staff.system.message";
    public static final String STAFF_OVERLAY_DATA = "staff.overlay.data";
    public static final String STAFF_HA_RECEIVED = "staff.ha.received";
    public static final String STAFF_SUMMONED = "staff.summoned";

    // System
    public static final String SYSTEM_PING = "system.ping";
    public static final String SYSTEM_CLIENT_VERSION = "system.client.version";
    public static final String SYSTEM_FEATURE_FLAGS = "system.feature_flags";
    public static final String SYSTEM_TELEMETRY = "system.telemetry";
    public static final String SYSTEM_PONG = "system.pong";
    public static final String SYSTEM_MOTD = "system.motd";
    public static final String SYSTEM_NOTICE = "system.notice";
    public static final String SYSTEM_MAINTENANCE_SCHEDULED = "system.maintenance.scheduled";
    public static final String SYSTEM_MAINTENANCE_IMMINENT = "system.maintenance.imminent";
    public static final String SYSTEM_SERVER_RESTART = "system.server.restart";
    public static final String SYSTEM_CURRENCY_UPDATE = "system.currency.update";
    public static final String SYSTEM_FEATURE_FLAGS_RESULT = "system.feature_flags.result";

    // Events (Phase 16)
    public static final String EVT_LIST              = "event.list";
    public static final String EVT_LIST_RESULT       = "event.list.result";
    public static final String EVT_INFO              = "event.info";
    public static final String EVT_INFO_RESULT       = "event.info.result";
    public static final String EVT_CREATE            = "event.create";
    public static final String EVT_CREATED           = "event.created";
    public static final String EVT_JOIN              = "event.join";
    public static final String EVT_LEAVE             = "event.leave";
    public static final String EVT_STARTED           = "event.started";
    public static final String EVT_ENDED             = "event.ended";

    // Competitions (Phase 16)
    public static final String CMP_LIST              = "competition.list";
    public static final String CMP_LIST_RESULT       = "competition.list.result";
    public static final String CMP_INFO              = "competition.info";
    public static final String CMP_INFO_RESULT       = "competition.info.result";
    public static final String CMP_REGISTER          = "competition.register";
    public static final String CMP_SCORE_SUBMIT      = "competition.score.submit";
    public static final String CMP_LEADERBOARD       = "competition.leaderboard";
    public static final String CMP_LEADERBOARD_RESULT= "competition.leaderboard.result";
    public static final String CMP_FINALISED         = "competition.finalised";

    // Seasons (Phase 16)
    public static final String SEA_CURRENT           = "season.current";
    public static final String SEA_CURRENT_RESULT    = "season.current.result";
    public static final String SEA_PROGRESS          = "season.progress";
    public static final String SEA_PROGRESS_RESULT   = "season.progress.result";
    public static final String SEA_LEADERBOARD       = "season.leaderboard";
    public static final String SEA_LEADERBOARD_RESULT= "season.leaderboard.result";
    public static final String SEA_CLAIM_REWARDS     = "season.claim.rewards";
    public static final String SEA_REWARDS_CLAIMED   = "season.rewards.claimed";
    public static final String SEA_CHANGED           = "season.changed";
}
