import { create } from 'zustand';
import { getWsClient } from '@/ws/WsClient';
import { Packet } from '@/protocol/packets';

/**
 * What the player knows about their own mute.
 *
 * A message that simply never appears looks like the hotel dropping it. Holding
 * the mute here means the chat box can say what happened, and — when the mute
 * came from the content policy rather than a moderator — offer the one thing
 * that matters: a way to have a person look at it.
 */

/** Why the content policy stopped a message. */
export type MuteCategory =
  | 'hate'
  | 'threat'
  | 'minor_safety'
  | 'doxxing'
  | 'self_harm'
  | 'scam';

/** Plain-English wording for each category, for the player rather than staff. */
const CATEGORY_WORDING: Record<MuteCategory, string> = {
  hate: 'hatred aimed at who somebody is',
  threat: 'a threat',
  minor_safety: 'something unsafe involving a child',
  doxxing: "somebody's private details",
  self_harm: 'telling somebody to hurt themselves',
  scam: 'trying to take somebody’s account',
};

export interface MuteState {
  muted: boolean;
  /** True when the content policy muted them rather than a staff member. */
  automatic: boolean;
  category: MuteCategory | null;
  reason: string | null;
  expiresAt: string | null;
  canAskForHelp: boolean;
  secondsUntilHelpAllowed: number;
  /** The reply to the last help request, shown until it is dismissed. */
  notice: string | null;

  askForHelp: (message: string) => void;
  dismissNotice: () => void;
  refresh: () => void;
}

const CLEAR = {
  muted: false,
  automatic: false,
  category: null,
  reason: null,
  expiresAt: null,
  canAskForHelp: false,
  secondsUntilHelpAllowed: 0,
} as const;

export const useMuteStore = create<MuteState>((set) => ({
  ...CLEAR,
  notice: null,

  askForHelp(message: string) {
    getWsClient().send(Packet.MOD_AUTO_MUTE_HELP, { message });
  },

  dismissNotice() {
    set({ notice: null });
  },

  refresh() {
    getWsClient().send(Packet.MOD_AUTO_MUTE_STATE, {});
  },
}));

/** Turns a category into something worth reading. */
export function describeMute(state: MuteState): string {
  if (!state.muted && state.category === null) return '';

  if (!state.automatic) {
    return state.reason
      ? `A moderator has muted you: ${state.reason}`
      : 'A moderator has muted you.';
  }

  const what = state.category ? CATEGORY_WORDING[state.category] : 'something that broke the rules';
  return `That message was stopped for ${what}, and you are muted while a staff member reads it.`;
}

/** How long is left on a mute, in words. */
export function describeRemaining(expiresAt: string | null, now = Date.now()): string {
  if (!expiresAt) return '';

  const minutes = Math.ceil((new Date(expiresAt).getTime() - now) / 60_000);
  if (minutes <= 0) return 'any moment now';
  if (minutes < 60) return `${minutes} ${minutes === 1 ? 'minute' : 'minutes'}`;

  const hours = Math.round(minutes / 60);
  if (hours < 48) return `${hours} ${hours === 1 ? 'hour' : 'hours'}`;

  return `${Math.round(hours / 24)} days`;
}

export function initMuteListeners(): void {
  const ws = getWsClient();

  ws.on(Packet.MOD_AUTO_MUTE_NOTICE, (raw) => {
    const p = raw as {
      category?: MuteCategory;
      expiresAt?: string;
      canAskForHelp?: boolean;
      message?: string;
    };
    useMuteStore.setState({
      muted: true,
      automatic: true,
      category: p.category ?? null,
      reason: null,
      expiresAt: p.expiresAt ?? null,
      canAskForHelp: p.canAskForHelp ?? true,
      secondsUntilHelpAllowed: 0,
      notice: p.message ?? null,
    });
  });

  ws.on(Packet.MOD_AUTO_MUTE_STATE, (raw) => {
    const p = raw as {
      muted?: boolean;
      automatic?: boolean;
      category?: MuteCategory;
      reason?: string;
      expiresAt?: string;
      canAskForHelp?: boolean;
      secondsUntilHelpAllowed?: number;
    };

    if (!p.muted) {
      useMuteStore.setState({ ...CLEAR });
      return;
    }

    useMuteStore.setState({
      muted: true,
      automatic: p.automatic ?? false,
      category: p.category ?? null,
      reason: p.reason ?? null,
      expiresAt: p.expiresAt ?? null,
      canAskForHelp: p.canAskForHelp ?? false,
      secondsUntilHelpAllowed: p.secondsUntilHelpAllowed ?? 0,
    });
  });

  ws.on(Packet.MOD_AUTO_MUTE_HELP_ACK, (raw) => {
    const p = raw as { sent?: boolean; secondsUntilHelpAllowed?: number; message?: string };
    useMuteStore.setState({
      notice: p.message ?? null,
      canAskForHelp: false,
      secondsUntilHelpAllowed: p.secondsUntilHelpAllowed ?? 0,
    });
  });
}
