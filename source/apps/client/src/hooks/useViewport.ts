import { useEffect, useState } from 'react';

/**
 * What kind of screen this is.
 *
 * The hotel's layout assumes a mouse and a wide window: panels docked to the
 * right, a toolbar along the bottom, a room filling what is left. On a phone
 * there is no room left, so the same panels have to take the whole screen and
 * the toolbar has to scroll.
 *
 * Touch is asked about separately from width, because the two do not go
 * together as often as they used to: a laptop can have a touchscreen and a
 * tablet can be wider than a small laptop.
 */

/** Below this the layout has to change shape, not just get narrower. */
export const COMPACT_WIDTH = 720;

export interface Viewport {
  width: number;
  height: number;
  /** True when the screen is too narrow for the docked layout. */
  compact: boolean;
  /** True when the primary input is a finger rather than a pointer. */
  touch: boolean;
}

/** Reads the viewport once, safely enough to run before the window exists. */
export function readViewport(): Viewport {
  if (typeof window === 'undefined') {
    return { width: 1280, height: 800, compact: false, touch: false };
  }

  const width = window.innerWidth;
  return {
    width,
    height: window.innerHeight,
    compact: width < COMPACT_WIDTH,
    touch: hasTouch(),
  };
}

/** True when the device's main input is touch. */
export function hasTouch(): boolean {
  if (typeof window === 'undefined') return false;
  try {
    // A coarse pointer is the honest question: it covers a phone and a tablet
    // without catching a laptop that merely has a touchscreen alongside a
    // trackpad.
    if (window.matchMedia?.('(pointer: coarse)').matches) return true;
  } catch {
    // Fall through to the capability check.
  }
  return 'ontouchstart' in window || navigator.maxTouchPoints > 0;
}

export function useViewport(): Viewport {
  const [viewport, setViewport] = useState<Viewport>(readViewport);

  useEffect(() => {
    const update = () => setViewport(readViewport());

    window.addEventListener('resize', update);
    // A phone rotating changes the shape of the layout as surely as a resize.
    window.addEventListener('orientationchange', update);

    return () => {
      window.removeEventListener('resize', update);
      window.removeEventListener('orientationchange', update);
    };
  }, []);

  return viewport;
}
