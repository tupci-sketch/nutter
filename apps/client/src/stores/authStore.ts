import { create } from 'zustand';
import { getWsClient } from '@/ws/WsClient';
import { Packet } from '@/protocol/packets';

export interface AuthState {
  userId: number | null;
  username: string | null;
  rank: number;
  credits: number;
  diamonds: number;
  nutPoints: number;
  authenticated: boolean;

  login: (ticket: string) => void;
  logout: () => void;
  setBalance: (credits: number, diamonds: number, nutPoints: number) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  userId: null,
  username: null,
  rank: 0,
  credits: 0,
  diamonds: 0,
  nutPoints: 0,
  authenticated: false,

  login(ticket: string) {
    getWsClient().send(Packet.AUTH_LOGIN, { ticket });
  },

  logout() {
    getWsClient().send(Packet.AUTH_LOGOUT, {});
    set({ userId: null, username: null, rank: 0, authenticated: false });
  },

  setBalance(credits, diamonds, nutPoints) {
    set({ credits, diamonds, nutPoints });
  },
}));

export function initAuthListeners(): void {
  const ws = getWsClient();
  ws.on(Packet.AUTH_LOGIN_RESPONSE, (raw) => {
    const p = raw as {
      ok: boolean;
      userId: number;
      username: string;
      rank: number;
      credits: number;
      diamonds: number;
      nutPoints: number;
    };
    if (p.ok) {
      useAuthStore.setState({
        userId: p.userId,
        username: p.username,
        rank: p.rank,
        credits: p.credits,
        diamonds: p.diamonds,
        nutPoints: p.nutPoints,
        authenticated: true,
      });
    }
  });

  ws.on(Packet.WALLET_BALANCE_RESULT, (raw) => {
    const p = raw as { credits: number; diamonds: number; nutPoints: number };
    useAuthStore.getState().setBalance(p.credits, p.diamonds, p.nutPoints);
  });
}
