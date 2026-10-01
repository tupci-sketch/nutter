import { describe, it, expect, beforeEach, vi } from 'vitest';

/**
 * Getting in.
 *
 * One account, signed in once on the website. The hotel's reply is the shape
 * AuthHandler builds; the client used to listen for a packet the hotel never
 * sends, so a successful sign-in looked exactly like a failed one.
 */

type Handler = (payload: unknown) => void;
const handlers = new Map<string, Handler>();
const sent: Array<{ type: string; payload: unknown }> = [];

vi.mock('@/ws/WsClient', () => ({
  getWsClient: () => ({
    on(type: string, handler: Handler) {
      handlers.set(type, handler);
      return () => handlers.delete(type);
    },
    send(type: string, payload: unknown) {
      sent.push({ type, payload });
    },
  }),
}));

const { useAuthStore, initAuthListeners, startHandoff } = await import('../authStore');

function deliver(type: string, payload: unknown): void {
  const handler = handlers.get(type);
  if (!handler) throw new Error(`Nothing is listening for ${type}`);
  handler(payload);
}

const LOGIN_SUCCESS = {
  userId: 7,
  username: 'tupci',
  figureString: 'hd-180-1.ch-210-66',
  rank: 7,
  credits: 500,
  diamonds: 10,
  nutPoints: 250,
  lastLogin: '2026-09-30T10:00:00Z',
  worldId: 'classic',
};

beforeEach(() => {
  handlers.clear();
  sent.length = 0;
  useAuthStore.setState({
    userId: null, username: null, figureString: '', rank: 0,
    credits: 0, diamonds: 0, nutPoints: 0, worldId: null, lastLogin: null,
    phase: 'idle', error: null, handedOver: false, authenticated: false,
  });
  initAuthListeners();
});

describe('signing in', () => {
  it('sends the ticket the hotel is waiting for', () => {
    useAuthStore.getState().login('a-ticket');

    expect(sent).toContainEqual({ type: 'auth.login', payload: { ticket: 'a-ticket' } });
    expect(useAuthStore.getState().phase).toBe('connecting');
  });

  it('takes the hotel at its word when it lets us in', () => {
    deliver('auth.login.success', LOGIN_SUCCESS);

    const s = useAuthStore.getState();
    expect(s.authenticated).toBe(true);
    expect(s.phase).toBe('authenticated');
    expect(s.username).toBe('tupci');
    expect(s.figureString).toBe('hd-180-1.ch-210-66');
    expect(s.credits).toBe(500);
    expect(s.worldId).toBe('classic');
  });

  it('says why when the hotel turns us away', () => {
    deliver('auth.login.error', { code: 'auth.invalid_ticket', message: 'Invalid or expired ticket' });

    const s = useAuthStore.getState();
    expect(s.authenticated).toBe(false);
    expect(s.phase).toBe('failed');
    expect(s.error).toBe('Invalid or expired ticket');
  });

  it('explains a sign-in somewhere else rather than just dropping out', () => {
    deliver('auth.login.success', LOGIN_SUCCESS);
    deliver('auth.kicked', { reason: 'logged_in_elsewhere' });

    const s = useAuthStore.getState();
    expect(s.authenticated).toBe(false);
    expect(s.error).toMatch(/signed in somewhere else/i);
  });
});

describe('moving between worlds', () => {
  it('asks the hotel, then signs in again with what it hands back', () => {
    deliver('auth.login.success', LOGIN_SUCCESS);
    useAuthStore.getState().switchWorld('nutropolis');

    expect(sent).toContainEqual({
      type: 'auth.world.switch', payload: { worldId: 'nutropolis' },
    });

    deliver('auth.world.switched', { worldId: 'nutropolis', ticket: 'second-ticket' });

    expect(sent).toContainEqual({ type: 'auth.login', payload: { ticket: 'second-ticket' } });
    expect(useAuthStore.getState().worldId).toBe('nutropolis');
  });

  it('stays signed in when the move is refused', () => {
    deliver('auth.login.success', LOGIN_SUCCESS);
    useAuthStore.getState().switchWorld('nowhere');
    deliver('auth.world.switch.error', { message: 'No such world' });

    const s = useAuthStore.getState();
    expect(s.phase).toBe('authenticated');
    expect(s.error).toBe('No such world');
  });
});

describe('balances', () => {
  it('follows what the hotel says they are now', () => {
    deliver('auth.login.success', LOGIN_SUCCESS);
    deliver('system.currency.update', { credits: 450 });

    const s = useAuthStore.getState();
    expect(s.credits).toBe(450);
    expect(s.diamonds).toBe(10);
  });
});

describe('arriving from the website', () => {
  function at(href: string) {
    Object.defineProperty(window, 'location', {
      value: new URL(href), writable: true, configurable: true,
    });
    Object.defineProperty(window, 'history', {
      value: { replaceState: vi.fn() }, writable: true, configurable: true,
    });
  }

  it('uses the ticket the site left, without asking for one', () => {
    at('https://example.test/client/?ticket=from-the-site');

    expect(startHandoff()).toBe(true);
    expect(sent).toContainEqual({
      type: 'auth.login', payload: { ticket: 'from-the-site' },
    });
    expect(useAuthStore.getState().handedOver).toBe(true);
  });

  it('falls back to asking when there is no ticket in the address', () => {
    at('https://example.test/client/');

    expect(startHandoff()).toBe(false);
    expect(sent).toHaveLength(0);
  });
});
