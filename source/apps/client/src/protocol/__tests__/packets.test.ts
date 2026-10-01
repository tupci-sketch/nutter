import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { Packet } from '../packets';
import { generate, TS_TARGET } from '../../../scripts/generate-packets.mjs';

/**
 * The client and the hotel must mean the same thing by a packet name.
 *
 * This file was once written by hand, independently of the server's, and the
 * two drifted so far apart that almost nothing the client sent had a handler
 * and almost nothing the server sent was listened to — the client could open a
 * socket and do nothing else. It is generated now, and this is what keeps it
 * that way.
 */

describe('the packet table', () => {
  it('is exactly what the generator produces from the server', () => {
    expect(readFileSync(TS_TARGET, 'utf8')).toBe(generate());
  });

  it('carries the packets sign-in depends on', () => {
    expect(Packet.AUTH_LOGIN).toBe('auth.login');
    expect(Packet.AUTH_LOGIN_SUCCESS).toBe('auth.login.success');
    expect(Packet.AUTH_LOGIN_ERROR).toBe('auth.login.error');
  });

  it('carries the packets walking into a room depends on', () => {
    expect(Packet.ROOM_ENTER).toBe('room.enter');
    expect(Packet.ROOM_ENTER_SUCCESS).toBe('room.enter.success');
    expect(Packet.ROOM_USER_MOVED).toBe('room.user.moved');
  });

  it('gives every name one meaning', () => {
    const values = Object.values(Packet);
    expect(new Set(values).size).toBe(values.length);
  });

  it('names every packet in the dotted style the hotel uses', () => {
    for (const value of Object.values(Packet)) {
      expect(value).toMatch(/^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$/);
    }
  });
});
