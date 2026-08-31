import { describe, it, expect } from 'vitest';
import { describeMute, describeRemaining, type MuteState } from '../muteStore';

/**
 * What a muted player is told.
 *
 * A message that simply never appears looks like the hotel dropping it, so the
 * wording is part of the feature rather than decoration around it.
 */

function state(overrides: Partial<MuteState> = {}): MuteState {
  return {
    muted: true,
    automatic: true,
    category: 'threat',
    reason: null,
    expiresAt: null,
    canAskForHelp: true,
    secondsUntilHelpAllowed: 0,
    notice: null,
    askForHelp: () => undefined,
    dismissNotice: () => undefined,
    refresh: () => undefined,
    ...overrides,
  };
}

describe('describeMute', () => {
  it('says what was stopped and that somebody will read it', () => {
    const text = describeMute(state({ category: 'threat' }));
    expect(text).toContain('a threat');
    expect(text).toContain('staff member');
  });

  it('speaks plainly rather than naming the rule', () => {
    // Telling somebody exactly which expression caught them is a manual for
    // getting around it.
    const text = describeMute(state({ category: 'minor_safety' }));
    expect(text).toContain('unsafe involving a child');
    expect(text).not.toContain('minor_safety');
  });

  it('gives a moderator’s own reason when one muted them', () => {
    const text = describeMute(state({ automatic: false, reason: 'Told to stop' }));
    expect(text).toContain('A moderator');
    expect(text).toContain('Told to stop');
  });

  it('still says something when a moderator gave no reason', () => {
    expect(describeMute(state({ automatic: false, reason: null }))).toContain('A moderator');
  });

  it('says nothing when there is no mute', () => {
    expect(describeMute(state({ muted: false, category: null }))).toBe('');
  });
});

describe('describeRemaining', () => {
  const now = new Date('2026-01-01T12:00:00Z').getTime();
  const from = (minutes: number) =>
    new Date(now + minutes * 60_000).toISOString();

  it('counts in minutes for a short mute', () => {
    expect(describeRemaining(from(45), now)).toBe('45 minutes');
    expect(describeRemaining(from(1), now)).toBe('1 minute');
  });

  it('counts in hours once minutes stop being useful', () => {
    expect(describeRemaining(from(180), now)).toBe('3 hours');
  });

  it('counts in days for a long one', () => {
    expect(describeRemaining(from(60 * 24 * 7), now)).toBe('7 days');
  });

  it('does not count backwards once the mute is up', () => {
    expect(describeRemaining(from(-10), now)).toBe('any moment now');
  });

  it('says nothing without an expiry', () => {
    expect(describeRemaining(null, now)).toBe('');
  });
});
