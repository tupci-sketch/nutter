package com.habnut.emulator.protocol;

public final class ErrorCode {
    private ErrorCode() {}

    public static final String AUTH_INVALID_TICKET = "auth.invalid_ticket";
    public static final String AUTH_TICKET_EXPIRED = "auth.ticket_expired";
    public static final String AUTH_BANNED = "auth.banned";
    public static final String AUTH_ALREADY_CONNECTED = "auth.already_connected";
    public static final String AUTH_ALREADY_LOGGED_IN = "auth.already_logged_in";
    public static final String AUTH_NOT_AUTHENTICATED = "auth.not_authenticated";
    public static final String AUTH_INVALID_CREDENTIALS = "auth.invalid_credentials";
    public static final String AUTH_EMAIL_NOT_VERIFIED = "auth.email_not_verified";
    public static final String AUTH_2FA_REQUIRED = "auth.2fa_required";
    public static final String AUTH_2FA_INVALID = "auth.2fa_invalid";
    public static final String AUTH_RATE_LIMITED = "auth.rate_limited";

    public static final String ROOM_NOT_FOUND = "room.not_found";
    public static final String ROOM_ACCESS_DENIED = "room.access_denied";
    public static final String ROOM_FULL = "room.full";
    public static final String ROOM_BANNED = "room.banned";
    public static final String ROOM_DOORBELL_DECLINED = "room.doorbell_declined";

    public static final String FURNI_NOT_FOUND = "furni.not_found";
    public static final String FURNI_PLACEMENT_INVALID = "furni.placement_invalid";
    public static final String FURNI_PERMISSION_DENIED = "furni.permission_denied";
    public static final String FURNI_NOT_IN_INVENTORY = "furni.not_in_inventory";
    public static final String FURNI_STACK_LIMIT = "furni.stack_limit";

    public static final String ECO_INSUFFICIENT_BALANCE = "eco.insufficient_balance";
    public static final String ECO_INSUFFICIENT_CREDITS = "eco.insufficient_credits";
    public static final String ECO_INSUFFICIENT_DIAMONDS = "eco.insufficient_diamonds";
    public static final String ECO_IDEMPOTENCY_CONFLICT = "eco.idempotency_conflict";
    public static final String ECO_TRANSACTION_FAILED = "eco.transaction_failed";

    public static final String CAT_ITEM_NOT_FOUND = "cat.item_not_found";
    public static final String CAT_ITEM_NOT_AVAILABLE = "cat.item_not_available";
    public static final String CAT_LIMITED_SOLD_OUT = "cat.limited_sold_out";
    public static final String CAT_PURCHASE_LIMIT_REACHED = "cat.purchase_limit_reached";

    public static final String TRADE_NOT_FOUND = "trade.not_found";
    public static final String TRADE_INVALID_PARTNER = "trade.invalid_partner";
    public static final String TRADE_ALREADY_IN_PROGRESS = "trade.already_in_progress";
    public static final String TRADE_NOT_IN_PROGRESS = "trade.not_in_progress";
    public static final String TRADE_ALREADY_ACCEPTED = "trade.already_accepted";
    public static final String TRADE_ITEM_NOT_OWNED = "trade.item_not_owned";
    public static final String TRADE_PARTNER_OFFLINE = "trade.partner_offline";

    public static final String WIRED_VARIABLE_NOT_FOUND = "wired.variable_not_found";
    public static final String WIRED_INVALID_EXPRESSION = "wired.invalid_expression";
    public static final String WIRED_EXECUTION_LIMIT = "wired.execution_limit";
    public static final String WIRED_CYCLE_DETECTED = "wired.cycle_detected";

    public static final String MOD_PERMISSION_DENIED = "mod.permission_denied";
    public static final String MOD_TARGET_NOT_FOUND = "mod.target_not_found";
    public static final String MOD_ALREADY_BANNED = "mod.already_banned";

    public static final String RP_CHARACTER_NOT_FOUND = "rp.character_not_found";
    public static final String RP_INSUFFICIENT_CASH = "rp.insufficient_cash";
    public static final String RP_LICENCE_REQUIRED = "rp.licence_required";
    public static final String RP_IN_CUSTODY = "rp.in_custody";

    public static final String GARDEN_PLOT_NOT_FOUND = "garden.plot_not_found";
    public static final String GARDEN_PLANT_WITHERED = "garden.plant_withered";

    public static final String GENERIC_RATE_LIMITED = "generic.rate_limited";
    public static final String GENERIC_INTERNAL_ERROR = "generic.internal_error";
    public static final String GENERIC_NOT_FOUND = "generic.not_found";
    public static final String GENERIC_PERMISSION_DENIED = "generic.permission_denied";
    public static final String GENERIC_INVALID_PAYLOAD = "generic.invalid_payload";
}
