import { describe, it, expect, vi, afterEach } from 'vitest';
import { COMPACT_WIDTH, hasTouch, readViewport } from '../useViewport';

/**
 * Deciding what kind of screen this is.
 *
 * The layout changes shape below a width, so the boundary is worth pinning
 * down, and every one of these has to survive a browser that answers none of
 * the questions.
 */

afterEach(() => {
  vi.unstubAllGlobals();
});

function withWindow(width: number, matchMedia?: unknown) {
  vi.stubGlobal('window', {
    innerWidth: width,
    innerHeight: 800,
    matchMedia,
    navigator: { maxTouchPoints: 0 },
  });
  vi.stubGlobal('navigator', { maxTouchPoints: 0 });
}

describe('readViewport', () => {
  it('calls a phone-width window compact', () => {
    withWindow(COMPACT_WIDTH - 1);
    expect(readViewport().compact).toBe(true);
  });

  it('does not call a desktop window compact', () => {
    withWindow(COMPACT_WIDTH + 1);
    expect(readViewport().compact).toBe(false);
  });

  it('treats the boundary itself as roomy', () => {
    withWindow(COMPACT_WIDTH);
    expect(readViewport().compact).toBe(false);
  });
});

describe('hasTouch', () => {
  it('trusts a coarse pointer', () => {
    withWindow(400, (query: string) => ({ matches: query.includes('coarse') }));
    expect(hasTouch()).toBe(true);
  });

  it('is false for a fine pointer with no touch points', () => {
    withWindow(1400, () => ({ matches: false }));
    expect(hasTouch()).toBe(false);
  });

  it('falls back to touch points when the query throws', () => {
    vi.stubGlobal('window', {
      innerWidth: 400,
      innerHeight: 800,
      matchMedia: () => { throw new Error('unsupported'); },
    });
    vi.stubGlobal('navigator', { maxTouchPoints: 5 });

    // A browser that cannot answer the question should not make the hotel
    // decide there is no touchscreen on a device that plainly has one.
    expect(hasTouch()).toBe(true);
  });
});
