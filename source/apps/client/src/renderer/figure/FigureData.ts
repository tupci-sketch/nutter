/**
 * Figure data: the tables that describe how an avatar is assembled.
 *
 * Two files drive this. figuredata describes what parts exist, which are
 * colourable, and which palette each set draws its colours from. figuremap says
 * which sprite library a given part id actually lives in. Neither is optional:
 * without figuredata a figure renders in flat greyscale, and without figuremap
 * there is no way to turn a part id into a sprite name.
 */

/** A single colour in a palette. */
export interface FigureColour {
  id: string;
  /** 0xRRGGBB, ready to use as a tint. */
  rgb: number;
  /** Club level required to wear it; 0 is free to everyone. */
  clubLevel: number;
  selectable: boolean;
}

/** One wearable part within a set, e.g. a specific hairstyle's hair layer. */
export interface FigurePart {
  id: string;
  /** Part type such as "hr" for hair or "ch" for a shirt. */
  type: string;
  /** Draw order within the avatar; lower numbers sit further back. */
  index: number;
  colourable: boolean;
  /** Which of the figure's colour slots tints this part, 1-based. */
  colourIndex: number;
}

/** A set is one selectable option, e.g. hairstyle 828, made of several parts. */
export interface FigureSet {
  id: string;
  gender: string;
  clubLevel: number;
  colourable: boolean;
  selectable: boolean;
  parts: FigurePart[];
}

/** A set type groups every option for one slot, e.g. all hairstyles. */
export interface FigureSetType {
  type: string;
  paletteId: string;
  sets: Map<string, FigureSet>;
}

/** One segment of a parsed figure string. */
export interface FigurePartSelection {
  type: string;
  setId: string;
  colours: string[];
}

/**
 * The order avatar parts are drawn in, back to front.
 *
 * figuredata carries a per-part index, but it orders parts within a set rather
 * than across the whole avatar, so this table decides which slot sits in front
 * of which. It is the difference between a face drawn over hair and hair drawn
 * over a face.
 */
export const PART_DRAW_ORDER: readonly string[] = [
  'li', // left-hand item, behind everything
  'lh', // left hand
  'ls', // left sleeve
  'bd', // body
  'sh', // shoes
  'lg', // legs
  'ch', // chest / shirt
  'wa', // waist
  'ca', // chest accessory
  'cc', // coat
  'hd', // head
  'fc', // face
  'ey', // eyes
  'hr', // hair
  'hrb', // hair below a hat
  'ha', // hat
  'he', // head accessory
  'ea', // eye accessory / glasses
  'fa', // face accessory
  'rh', // right hand
  'rs', // right sleeve
  'ri', // right-hand item, in front
];

/** Draw-order rank for a part type; unknown types sort to the front. */
export function drawOrderOf(type: string): number {
  const i = PART_DRAW_ORDER.indexOf(type);
  return i === -1 ? PART_DRAW_ORDER.length : i;
}

export class FigureData {
  /** Palette id to colour id to colour. */
  private palettes = new Map<string, Map<string, FigureColour>>();
  /** Part type to its set type. */
  private setTypes = new Map<string, FigureSetType>();
  /** Part id to the sprite library that holds it, keyed "type:id". */
  private partLibraries = new Map<string, string>();

  // ─── parsing ──────────────────────────────────────────────────────────────

  /** Reads figuredata, which defines palettes and every selectable set. */
  parseFigureData(xml: string): void {
    const doc = new DOMParser().parseFromString(xml, 'text/xml');

    doc.querySelectorAll('colors palette, colours palette').forEach((palette) => {
      const paletteId = palette.getAttribute('id') ?? '';
      if (!paletteId) return;

      const colours = new Map<string, FigureColour>();
      palette.querySelectorAll('color, colour').forEach((colour) => {
        const id = colour.getAttribute('id') ?? '';
        if (!id) return;
        colours.set(id, {
          id,
          rgb: parseHexColour(colour.textContent),
          clubLevel: parseInt(colour.getAttribute('club') ?? '0', 10) || 0,
          selectable: colour.getAttribute('selectable') !== '0',
        });
      });
      this.palettes.set(paletteId, colours);
    });

    doc.querySelectorAll('sets settype').forEach((setType) => {
      const type = setType.getAttribute('type') ?? '';
      if (!type) return;

      const sets = new Map<string, FigureSet>();
      setType.querySelectorAll('set').forEach((set) => {
        const setId = set.getAttribute('id') ?? '';
        if (!setId) return;

        const parts: FigurePart[] = [];
        set.querySelectorAll('part').forEach((part) => {
          parts.push({
            id: part.getAttribute('id') ?? '0',
            type: part.getAttribute('type') ?? type,
            index: parseInt(part.getAttribute('index') ?? '0', 10) || 0,
            colourable: part.getAttribute('colorable') === '1'
              || part.getAttribute('colourable') === '1',
            colourIndex: parseInt(part.getAttribute('colorindex')
              ?? part.getAttribute('colourindex') ?? '1', 10) || 1,
          });
        });

        sets.set(setId, {
          id: setId,
          gender: set.getAttribute('gender') ?? 'U',
          clubLevel: parseInt(set.getAttribute('club') ?? '0', 10) || 0,
          colourable: set.getAttribute('colorable') === '1'
            || set.getAttribute('colourable') === '1',
          selectable: set.getAttribute('selectable') !== '0',
          parts,
        });
      });

      this.setTypes.set(type, {
        type,
        paletteId: setType.getAttribute('paletteid')
          ?? setType.getAttribute('paletteId') ?? '',
        sets,
      });
    });
  }

  /** Reads figuremap, which says which library each part id lives in. */
  parseFigureMap(xml: string): void {
    const doc = new DOMParser().parseFromString(xml, 'text/xml');
    doc.querySelectorAll('lib').forEach((lib) => {
      const libId = lib.getAttribute('id') ?? '';
      if (!libId) return;
      lib.querySelectorAll('part').forEach((part) => {
        const id = part.getAttribute('id');
        const type = part.getAttribute('type');
        if (!id || !type) return;
        this.partLibraries.set(`${type}:${id}`, libId);
      });
    });
  }

  // ─── lookups ──────────────────────────────────────────────────────────────

  /** True once figuredata has produced at least one set type. */
  get loaded(): boolean {
    return this.setTypes.size > 0;
  }

  /** The library holding a part, or undefined if the pack lacks it. */
  libraryFor(type: string, partId: string): string | undefined {
    return this.partLibraries.get(`${type}:${partId}`);
  }

  setType(type: string): FigureSetType | undefined {
    return this.setTypes.get(type);
  }

  set(type: string, setId: string): FigureSet | undefined {
    return this.setTypes.get(type)?.sets.get(setId);
  }

  /**
   * Resolves a colour to a tint.
   *
   * Colours are looked up in the palette belonging to the part's set type, so
   * the same colour id means different things for hair and for a shirt.
   */
  colourFor(type: string, colourId: string): FigureColour | undefined {
    const paletteId = this.setTypes.get(type)?.paletteId;
    if (!paletteId) return undefined;
    return this.palettes.get(paletteId)?.get(colourId);
  }

  /** Every set type present, for a figure editor to offer. */
  setTypeNames(): string[] {
    return [...this.setTypes.keys()];
  }
}

// ─── figure string ───────────────────────────────────────────────────────────

/**
 * Parses a figure string such as "hd-180-1.ch-255-66.lg-280-110".
 *
 * Malformed segments are dropped rather than throwing: a figure string arrives
 * from the database and from other players, and one bad segment should cost a
 * hat, not the whole avatar.
 */
export function parseFigureString(figure: string): FigurePartSelection[] {
  if (!figure) return [];

  const seen = new Set<string>();
  const out: FigurePartSelection[] = [];

  for (const segment of figure.split('.')) {
    if (!segment) continue;
    const bits = segment.split('-');
    const type = bits[0];
    const setId = bits[1];
    if (!type || !setId) continue;
    // A repeated slot would draw twice; the first wins.
    if (seen.has(type)) continue;
    seen.add(type);
    out.push({ type, setId, colours: bits.slice(2).filter(Boolean) });
  }
  return out;
}

/** Renders selections back to a figure string. */
export function buildFigureString(parts: FigurePartSelection[]): string {
  return parts
    .map((p) => [p.type, p.setId, ...p.colours].join('-'))
    .join('.');
}

/** Parses "#RRGGBB" or "RRGGBB" to a number, falling back to white. */
function parseHexColour(text: string | null): number {
  if (!text) return 0xffffff;
  const hex = text.trim().replace(/^#/, '');
  const value = parseInt(hex, 16);
  return Number.isNaN(value) ? 0xffffff : value;
}
