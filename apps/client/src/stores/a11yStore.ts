import { create } from 'zustand';

/**
 * How the client should behave for this player.
 *
 * These are not decorations. A room that animates constantly is unusable for
 * somebody who gets motion sick, an isometric canvas is invisible to a screen
 * reader unless it describes itself, and a hotel that says "your friend is
 * online" only by turning a dot green says nothing at all to the one player in
 * twelve who cannot tell the two greens apart.
 *
 * The defaults come from what the browser already knows, so somebody who has
 * asked their system for less motion does not have to ask again here.
 */

export interface A11ySettings {
  /** Stop walk cycles, transitions and smooth scrolling. */
  reducedMotion: boolean;
  /** Add a word or a shape wherever colour alone carries meaning. */
  neverColourAlone: boolean;
  /** Announce the room and what happens in it to a screen reader. */
  announceRoom: boolean;
  /** Make text and controls larger, for a small screen or tired eyes. */
  largeText: boolean;
}

export interface A11yStore extends A11ySettings {
  set: <K extends keyof A11ySettings>(key: K, value: A11ySettings[K]) => void;
  /** The last thing worth telling a screen reader about. */
  announcement: string;
  announce: (message: string) => void;
}

const STORAGE_KEY = 'habnut.accessibility';

/** Reads a media query without assuming the browser supports it. */
function prefers(query: string): boolean {
  try {
    return window.matchMedia?.(query).matches ?? false;
  } catch {
    return false;
  }
}

/**
 * The settings to start with.
 *
 * A stored choice wins, because the player made it here. Otherwise the system
 * preference decides, and only then a default.
 */
export function initialSettings(): A11ySettings {
  const fromSystem: A11ySettings = {
    reducedMotion: prefers('(prefers-reduced-motion: reduce)'),
    neverColourAlone: false,
    announceRoom: false,
    largeText: false,
  };

  try {
    const stored = window.localStorage?.getItem(STORAGE_KEY);
    if (!stored) return fromSystem;

    const parsed = JSON.parse(stored) as Partial<A11ySettings>;
    return {
      reducedMotion: parsed.reducedMotion ?? fromSystem.reducedMotion,
      neverColourAlone: parsed.neverColourAlone ?? fromSystem.neverColourAlone,
      announceRoom: parsed.announceRoom ?? fromSystem.announceRoom,
      largeText: parsed.largeText ?? fromSystem.largeText,
    };
  } catch {
    // A browser with storage switched off should still get the system's answer.
    return fromSystem;
  }
}

function persist(settings: A11ySettings): void {
  try {
    window.localStorage?.setItem(STORAGE_KEY, JSON.stringify(settings));
  } catch {
    // Nothing to do: the setting still applies for this session.
  }
}

export const useA11yStore = create<A11yStore>((set, get) => ({
  ...initialSettings(),
  announcement: '',

  set(key, value) {
    set({ [key]: value } as Partial<A11yStore>);

    const { reducedMotion, neverColourAlone, announceRoom, largeText } = get();
    persist({ reducedMotion, neverColourAlone, announceRoom, largeText });
  },

  announce(message) {
    // Only announce when asked to. A live region that speaks over everything is
    // worse than one that says nothing.
    if (!get().announceRoom) return;
    set({ announcement: message });
  },
}));

/** Reads the current settings outside React, for the renderer. */
export function motionIsReduced(): boolean {
  return useA11yStore.getState().reducedMotion;
}
