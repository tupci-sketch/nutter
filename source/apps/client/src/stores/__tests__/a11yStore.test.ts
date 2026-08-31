import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { initialSettings } from '../a11yStore';

/**
 * Where the accessibility settings start.
 *
 * Somebody who has already told their operating system they want less motion
 * should not have to tell the hotel as well, and somebody who told the hotel
 * something different should have that respected over the system.
 */

const store = new Map<string, string>();

beforeEach(() => {
  store.clear();
  vi.stubGlobal('window', {
    matchMedia: (query: string) => ({ matches: query.includes('reduce') }),
    localStorage: {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => void store.set(k, v),
    },
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('initialSettings', () => {
  it('takes less motion from the system when nothing is stored', () => {
    expect(initialSettings().reducedMotion).toBe(true);
  });

  it('lets a stored choice override the system', () => {
    store.set('habnut.accessibility', JSON.stringify({ reducedMotion: false }));

    expect(initialSettings().reducedMotion).toBe(false);
  });

  it('fills in anything a stored choice does not mention', () => {
    store.set('habnut.accessibility', JSON.stringify({ largeText: true }));

    const settings = initialSettings();
    expect(settings.largeText).toBe(true);
    // Not mentioned, so the system still decides.
    expect(settings.reducedMotion).toBe(true);
  });

  it('ignores stored rubbish rather than failing to start', () => {
    store.set('habnut.accessibility', 'not json');

    expect(() => initialSettings()).not.toThrow();
    expect(initialSettings().reducedMotion).toBe(true);
  });

  it('works in a browser with no storage and no media queries', () => {
    vi.stubGlobal('window', {});

    const settings = initialSettings();
    expect(settings.reducedMotion).toBe(false);
    expect(settings.announceRoom).toBe(false);
  });
});
