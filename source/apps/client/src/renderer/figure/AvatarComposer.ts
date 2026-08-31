/**
 * Avatar composition: turning a figure string into an ordered list of sprites.
 *
 * This is deliberately free of any renderer. It decides which sprite names to
 * draw, in what order, tinted which colour, and whether each is mirrored —
 * nothing more. That keeps the rules that are easy to get subtly wrong (draw
 * order, direction mirroring, which colour slot tints which part) testable
 * without standing up a canvas.
 */

import {
  FigureData,
  drawOrderOf,
  parseFigureString,
  type FigurePartSelection,
} from './FigureData';

/** Sprite size: 'h' is the room size, 'sh' the small head used in lists. */
export type FigureSize = 'h' | 'sh';

/** Avatar actions, in the sprite-name form the pack uses. */
export const ACTION = {
  Stand: 'std',
  Walk: 'wlk',
  Sit: 'sit',
  Lay: 'lay',
  Wave: 'wav',
  Respect: 'respect',
  Blow: 'blow',
} as const;

export type FigureAction = (typeof ACTION)[keyof typeof ACTION];

/** One sprite to draw, already resolved to a name, order and tint. */
export interface AvatarLayer {
  /** Sprite name to look up in the manifest. */
  sprite: string;
  /** Draw order, back to front. */
  z: number;
  /** Tint as 0xRRGGBB, or null to draw the sprite unmodified. */
  tint: number | null;
  /** True when the pack expects this direction to be mirrored. */
  flip: boolean;
  /** The part type, useful for debugging and for hiding slots. */
  type: string;
}

export interface ComposeOptions {
  figure: string;
  direction: number;
  /** Head direction, which can differ from the body when a user looks around. */
  headDirection?: number;
  action?: FigureAction;
  frame?: number;
  size?: FigureSize;
  /** Part types to leave out, e.g. hair under a hat. */
  hide?: readonly string[];
}

/**
 * Directions 0-3 and 7 are drawn as stored. Directions 4, 5 and 6 are not in
 * the pack at all: they are 2, 1 and 0 mirrored. Composing them any other way
 * leaves a quarter of the compass with no avatar.
 */
export function mirrorDirection(direction: number): { direction: number; flip: boolean } {
  const d = ((direction % 8) + 8) % 8;
  if (d > 3 && d < 7) {
    return { direction: 6 - d, flip: true };
  }
  return { direction: d, flip: false };
}

/** Head-only part types, which follow the head direction rather than the body. */
const HEAD_PARTS = new Set(['hd', 'fc', 'ey', 'hr', 'hrb', 'ha', 'he', 'ea', 'fa']);

/**
 * Actions that only change the body. A waving avatar still has a normal head,
 * and a pack ships no head sprite for the wave action, so head parts fall back
 * to standing.
 */
const BODY_ONLY_ACTIONS = new Set<string>([ACTION.Wave, ACTION.Respect, ACTION.Blow]);

export class AvatarComposer {
  constructor(private readonly data: FigureData) {}

  /**
   * Builds the ordered layer list for a figure.
   *
   * Parts whose library is unknown are skipped: a pack that lacks one hairstyle
   * should cost that hairstyle, not the whole avatar.
   */
  compose(options: ComposeOptions): AvatarLayer[] {
    const {
      figure,
      direction,
      headDirection = direction,
      action = ACTION.Stand,
      frame = 0,
      size = 'h',
      hide = [],
    } = options;

    const hidden = new Set(hide);
    const selections = parseFigureString(figure);
    const layers: AvatarLayer[] = [];

    for (const selection of selections) {
      if (hidden.has(selection.type)) continue;

      const set = this.data.set(selection.type, selection.setId);
      if (!set) continue;

      for (const part of set.parts) {
        if (hidden.has(part.type)) continue;

        const library = this.data.libraryFor(part.type, part.id);
        if (!library) continue;

        const isHead = HEAD_PARTS.has(part.type);
        const partAction = isHead && BODY_ONLY_ACTIONS.has(action) ? ACTION.Stand : action;
        const facing = mirrorDirection(isHead ? headDirection : direction);

        layers.push({
          sprite: spriteName({
            library,
            size,
            action: partAction,
            type: part.type,
            partId: part.id,
            direction: facing.direction,
            frame,
          }),
          z: drawOrderOf(part.type) * 10 + part.index,
          tint: this.tintFor(selection, part),
          flip: facing.flip,
          type: part.type,
        });
      }
    }

    return layers.sort((a, b) => a.z - b.z);
  }

  /**
   * The tint for one part.
   *
   * Colourable parts ship as greyscale masks and take their colour from the
   * figure string; the part's colour index picks which of the figure's colour
   * slots applies. A part that is not colourable keeps the artwork's own
   * colours and must not be tinted.
   */
  private tintFor(selection: FigurePartSelection, part: { colourable: boolean; colourIndex: number }): number | null {
    if (!part.colourable) return null;

    const colourId = selection.colours[part.colourIndex - 1];
    if (!colourId) return null;

    return this.data.colourFor(selection.type, colourId)?.rgb ?? null;
  }
}

/**
 * Builds a sprite name in the form the pack uses:
 * {library}_{size}_{action}_{type}_{partId}_{direction}_{frame}
 *
 * For example hh_human_hair_h_std_hr_828_2_0.
 */
export function spriteName(parts: {
  library: string;
  size: FigureSize;
  action: string;
  type: string;
  partId: string;
  direction: number;
  frame: number;
}): string {
  return [
    parts.library,
    parts.size,
    parts.action,
    parts.type,
    parts.partId,
    parts.direction,
    parts.frame,
  ].join('_');
}
