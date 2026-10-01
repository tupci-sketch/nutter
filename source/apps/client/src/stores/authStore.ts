import { create } from 'zustand';
import { getWsClient } from '@/ws/WsClient';
import { Packet } from '@/protocol/packets';
import { readHandoff, clearHandoff } from '@/auth/handoff';
import { setSelfUserId } from '@/stores/roomStore';

/** Where we are in the business of getting into the hotel. */
export type AuthPhase =
  /** Nothing has been tried yet — the ticket box is waiting. */
  | 'idle'
  /** A ticket has been sent and we are waiting to hear back. */
  | 'connecting'
  /** We are in. */
  | 'authenticated'
  /** The ticket was no good, or the hotel turned us away. */
  | 'failed';

export interface AuthState {
  userId: number | null;
  username: string | null;
  figureString: string;
  rank: number;
  credits: number;
  diamonds: number;
  nutPoints: number;
  worldId: string | null;
  lastLogin: string | null;

  phase: AuthPhase;
  error: string | null;
  /** True while the ticket came from the website rather than being typed. */
  handedOver: boolean;

  authenticated: boolean;

  login: (ticket: string) => void;
  logout: () => void;
  switchWorld: (worldId: string) => void;
  setBalance: (credits: number, diamonds: number, nutPoints: number) => void;
}

export const useAuthStore = create<AuthState>(set => ({
  userId: null,
  username: null,
  figureString: '',
  rank: 0,
  credits: 0,
  diamonds: 0,
  nutPoints: 0,
  worldId: null,
  lastLogin: null,

  phase: 'idle',
  error: null,
  handedOver: false,

  authenticated: false,

  login(ticket: string) {
    set({ phase: 'connecting', error: null });
    getWsClient().send(Packet.AUTH_LOGIN, { ticket });
  },

  logout() {
    getWsClient().send(Packet.AUTH_LOGOUT, {});
    setSelfUserId(null);
    set({
      userId: null,
      username: null,
      rank: 0,
      authenticated: false,
      phase: 'idle',
      error: null,
    });
  },

  /**
   * Move between the hotel and the roleplay city.
   *
   * The hotel issues a fresh ticket for the world being moved to and sends it
   * back; we log in again with it, which is what keeps one account working
   * across both without a second sign-in.
   */
  switchWorld(worldId: string) {
    set({ phase: 'connecting', error: null });
    getWsClient().send(Packet.AUTH_WORLD_SWITCH, { worldId });
  },

  setBalance(credits, diamonds, nutPoints) {
    set({ credits, diamonds, nutPoints });
  },
}));

export function initAuthListeners(): void {
  const ws = getWsClient();

  ws.on(Packet.AUTH_LOGIN_SUCCESS, raw => {
    const p = raw as {
      userId: number;
      username: string;
      figureString: string;
      rank: number;
      credits: number;
      diamonds: number;
      nutPoints: number;
      worldId: string;
      lastLogin: string;
    };
    setSelfUserId(p.userId);
    useAuthStore.setState({
      userId: p.userId,
      username: p.username,
      figureString: p.figureString,
      rank: p.rank,
      credits: p.credits,
      diamonds: p.diamonds,
      nutPoints: p.nutPoints,
      worldId: p.worldId,
      lastLogin: p.lastLogin || null,
      authenticated: true,
      phase: 'authenticated',
      error: null,
    });
  });

  ws.on(Packet.AUTH_LOGIN_ERROR, raw => {
    const p = raw as { code?: string; message?: string; reason?: string };
    useAuthStore.setState({
      authenticated: false,
      phase: 'failed',
      error: p.message ?? p.reason ?? 'The hotel would not let us in.',
    });
  });

  // The hotel hands back a ticket for the world we asked to move to; using it
  // is the move.
  ws.on(Packet.AUTH_WORLD_SWITCHED, raw => {
    const p = raw as { worldId: string; ticket: string };
    if (p.ticket) {
      useAuthStore.setState({ worldId: p.worldId });
      useAuthStore.getState().login(p.ticket);
    }
  });

  ws.on(Packet.AUTH_WORLD_SWITCH_ERROR, raw => {
    const p = raw as { message?: string };
    useAuthStore.setState({
      phase: 'authenticated',
      error: p.message ?? 'Could not move to that world.',
    });
  });

  ws.on(Packet.AUTH_KICKED, raw => {
    const p = raw as { reason?: string };
    setSelfUserId(null);
    useAuthStore.setState({
      authenticated: false,
      phase: 'failed',
      error: p.reason === 'logged_in_elsewhere'
        ? 'You signed in somewhere else, so this window was signed out.'
        : 'You were signed out of the hotel.',
    });
  });

  ws.on(Packet.AUTH_SESSION_EXPIRED, () => {
    setSelfUserId(null);
    useAuthStore.setState({
      authenticated: false,
      phase: 'failed',
      error: 'Your session ran out. Sign in on the website again.',
    });
  });

  ws.on(Packet.SYSTEM_CURRENCY_UPDATE, raw => {
    const p = raw as { credits?: number; diamonds?: number; nutPoints?: number };
    useAuthStore.setState(s => ({
      credits: p.credits ?? s.credits,
      diamonds: p.diamonds ?? s.diamonds,
      nutPoints: p.nutPoints ?? s.nutPoints,
    }));
  });
}

/**
 * Use the ticket the website put in the address, if it left one.
 *
 * This is the whole point of signing in on the site: a player who has already
 * done so should never see a login box here. If there is no ticket we fall
 * through to the manual one, which is what a developer running the client on
 * its own needs.
 */
export function startHandoff(): boolean {
  const handoff = readHandoff();
  if (!handoff) return false;

  useAuthStore.setState({ handedOver: true });
  useAuthStore.getState().login(handoff.ticket);
  clearHandoff();
  return true;
}
