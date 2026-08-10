export const SystemPacketTypes = {
  // c2s
  PING: 'system.ping',
  CLIENT_VERSION: 'system.client.version',
  FEATURE_FLAGS: 'system.feature_flags',
  NOTICE_DISMISS: 'system.notice.dismiss',
  TELEMETRY: 'system.telemetry',

  // s2c
  PONG: 'system.pong',
  MOTD: 'system.motd',
  NOTICE: 'system.notice',
  FEATURE_FLAGS_RESULT: 'system.feature_flags.result',
  MAINTENANCE_SCHEDULED: 'system.maintenance.scheduled',
  MAINTENANCE_IMMINENT: 'system.maintenance.imminent',
  SYSTEM_ALERT: 'system.alert',
  SERVER_RESTART: 'system.server.restart',
  CURRENCY_UPDATE: 'system.currency.update',
} as const;

export type SystemPacketType = (typeof SystemPacketTypes)[keyof typeof SystemPacketTypes];

export interface SystemPingPayload {
  clientTimestamp: number;
}

export interface SystemPongPayload {
  clientTimestamp: number;
  serverTimestamp: number;
  latencyMs: number;
}

export interface SystemMotdPayload {
  title: string;
  body: string;
  link: string | null;
}

export interface SystemNoticePayload {
  noticeId: string;
  type: 'info' | 'warning' | 'promotion' | 'event';
  title: string;
  body: string;
  imageUrl: string | null;
  link: string | null;
  dismissible: boolean;
  expiresAt: string | null;
}

export interface SystemFeatureFlagsResultPayload {
  flags: Record<string, boolean | string | number>;
}

export interface SystemMaintenanceScheduledPayload {
  scheduledAt: string;
  durationEstimateMinutes: number;
  reason: string;
}

export interface SystemCurrencyUpdatePayload {
  credits: number;
  diamonds: number;
  nutPoints: number;
  seasonal: number;
}

export interface SystemClientVersionPayload {
  version: string;
  buildHash: string;
}

export interface SystemTelemetryPayload {
  events: Array<{
    name: string;
    data: Record<string, unknown>;
    clientTimestamp: number;
  }>;
}
