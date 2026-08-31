export const StaffPacketTypes = {
  // c2s
  COMMAND: 'staff.command',
  OVERLAY_REQUEST: 'staff.overlay.request',
  ROOM_INSPECT: 'staff.room.inspect',
  USER_LOCATE: 'staff.user.locate',
  HA: 'staff.ha',
  ALERT: 'staff.alert',
  TELEPORT: 'staff.teleport',
  SUMMON: 'staff.summon',
  UNMUTE_ALL: 'staff.room.unmute_all',
  ROOM_LOCK: 'staff.room.lock',
  ROOM_UNLOCK: 'staff.room.unlock',
  FEATURE_FLAG_SET: 'staff.feature_flag.set',
  SYSTEM_MESSAGE: 'staff.system.message',
  WIDGET_PREFS_SAVE: 'staff.widget.prefs.save',

  // s2c
  COMMAND_RESULT: 'staff.command.result',
  OVERLAY_DATA: 'staff.overlay.data',
  ROOM_INSPECT_RESULT: 'staff.room.inspect.result',
  USER_LOCATE_RESULT: 'staff.user.locate.result',
  HA_RECEIVED: 'staff.ha.received',
  ALERT_SENT: 'staff.alert.sent',
  TELEPORTED: 'staff.teleported',
  SUMMONED: 'staff.summoned',
  FEATURE_FLAG_UPDATED: 'staff.feature_flag.updated',
  WIDGET_PREFS_SAVED: 'staff.widget.prefs.saved',
  ERROR: 'staff.error',
} as const;

export type StaffPacketType = (typeof StaffPacketTypes)[keyof typeof StaffPacketTypes];

export interface StaffCommandPayload {
  command: string;
  args: string[];
}

export interface StaffHaPayload {
  message: string;
}

export interface StaffAlertPayload {
  message: string;
  targetUserId?: number;
  link?: string;
}

export interface StaffTeleportPayload {
  roomId: number;
}

export interface StaffSummonPayload {
  targetUserId: number;
}

export interface StaffOverlayData {
  server: {
    uptimeMs: number;
    playerCount: number;
    roomCount: number;
    jvmHeapUsedMb: number;
    jvmHeapMaxMb: number;
    cpuPercent: number;
    dbPoolSize: number;
    dbPoolActive: number;
    dbPoolIdle: number;
    redisConnected: boolean;
  };
  alerts: Array<{
    severity: 'critical' | 'warning' | 'info';
    message: string;
    since: string;
  }>;
}

export interface StaffRoomInspectResultPayload {
  roomId: number;
  roomName: string;
  ownerId: number;
  ownerName: string;
  playerCount: number;
  furniCount: number;
  wallFurniCount: number;
  wiredItemCount: number;
  chatLogCount: number;
  reportCount: number;
  flags: string[];
}

export interface StaffFeatureFlagSetPayload {
  flag: string;
  value: boolean | string | number;
}

export interface StaffSystemMessagePayload {
  message: string;
  link?: string;
}

export interface StaffWidgetPrefs {
  layout: Array<{
    widgetId: string;
    x: number;
    y: number;
    w: number;
    h: number;
  }>;
}
