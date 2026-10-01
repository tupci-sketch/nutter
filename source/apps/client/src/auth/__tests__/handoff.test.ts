import { describe, it, expect, beforeEach, vi } from 'vitest';
import { readHandoff, clearHandoff } from '../handoff';

/**
 * Coming in from the website.
 *
 * This is the only path a real player takes into the hotel, so it is worth
 * being sure about: the ticket is picked up, and it does not stay in the
 * address afterwards where a copied link would carry it to somebody else.
 */

describe('readHandoff', () => {
  it('finds the ticket the site left in the address', () => {
    expect(readHandoff('?ticket=abc123')).toEqual({ ticket: 'abc123', world: null });
  });

  it('carries the world when the site names one', () => {
    expect(readHandoff('?ticket=abc&world=nutropolis'))
      .toEqual({ ticket: 'abc', world: 'nutropolis' });
  });

  it('is nothing when there is no ticket', () => {
    expect(readHandoff('')).toBeNull();
    expect(readHandoff('?world=classic')).toBeNull();
  });

  it('treats a blank ticket as none, rather than sending an empty one', () => {
    expect(readHandoff('?ticket=')).toBeNull();
    expect(readHandoff('?ticket=%20%20')).toBeNull();
  });

  it('trims a ticket that picked up whitespace on the way', () => {
    expect(readHandoff('?ticket=%20abc%20')).toEqual({ ticket: 'abc', world: null });
  });
});

describe('clearHandoff', () => {
  const replaceState = vi.fn();

  beforeEach(() => {
    replaceState.mockClear();
    Object.defineProperty(window, 'history', {
      value: { replaceState },
      writable: true,
      configurable: true,
    });
  });

  function at(href: string) {
    Object.defineProperty(window, 'location', {
      value: new URL(href),
      writable: true,
      configurable: true,
    });
  }

  it('takes the ticket out of the address', () => {
    at('https://example.test/client/?ticket=secret');
    clearHandoff();
    expect(replaceState).toHaveBeenCalledWith({}, '', '/client/');
  });

  it('leaves everything else in the address alone', () => {
    at('https://example.test/client/?ticket=secret&world=nutropolis#room-5');
    clearHandoff();
    expect(replaceState).toHaveBeenCalledWith({}, '', '/client/?world=nutropolis#room-5');
  });

  it('does nothing when there was no ticket to remove', () => {
    at('https://example.test/client/');
    clearHandoff();
    expect(replaceState).not.toHaveBeenCalled();
  });
});
