import * as PIXI from 'pixi.js';
import { FigureData } from './figure/FigureData';
import { AvatarComposer } from './figure/AvatarComposer';

/**
 * The artwork a piece of furniture is drawn from. Colour variants are named
 * "{item}*{colour}" ("chair_norja*2") and share their base item's drawings.
 */
export function artworkOf(item: string): string {
  const star = item.indexOf('*');
  return star > 0 ? item.slice(0, star) : item;
}

// ─── types ───────────────────────────────────────────────────────────────────

/**
 * Which visual era the hotel is drawn in.
 *
 * Both packs describe the same rooms, the same furniture and the same figures;
 * they differ only in artwork. A player switching era stays in the room they
 * are standing in, on the same world, talking to the same people.
 */
export type AssetEra = 'classic' | 'modern';

export const ASSET_ERAS: readonly AssetEra[] = ['classic', 'modern'];

export function isAssetEra(value: string): value is AssetEra {
  return (ASSET_ERAS as readonly string[]).includes(value);
}

export interface FurniInfo {
  id: string;
  type: 'S' | 'I';
  revision: number;
  xdim: number;
  ydim: number;
  name: string;
  description: string;
  canSitOn: boolean;
  canStandOn: boolean;
  canLayOn: boolean;
  defaultDir: number;
}

/** A sprite as recorded in the manifest, before alias resolution. */
interface SpriteEntry {
  file?: string;
  width?: number;
  height?: number;
  offsetX?: number;
  offsetY?: number;
  /** Names the sprite this one mirrors, when it ships no image of its own. */
  source?: string;
  flipH?: boolean;
}

interface SpriteManifest {
  sprites: Record<string, SpriteEntry>;
}

/** A sprite resolved through any aliases, ready to draw. */
export interface ResolvedSprite {
  texture: PIXI.Texture;
  offsetX: number;
  offsetY: number;
  /** True when the artwork must be mirrored to face the requested way. */
  flip: boolean;
}

type EraListener = (era: AssetEra) => void;

// ─── loader ──────────────────────────────────────────────────────────────────

class AssetLoaderImpl {
  private baseUrl = '/assets';
  private era: AssetEra = 'modern';

  private textureCache = new Map<string, PIXI.Texture>();
  private furnidata = new Map<string, FurniInfo>();
  private manifest: SpriteManifest = { sprites: {} };

  private figureData = new FigureData();
  private composerInstance = new AvatarComposer(this.figureData);

  private ready = false;
  private initPromise: Promise<void> | null = null;
  private eraListeners = new Set<EraListener>();

  /** How deep an alias chain may go before it is treated as a loop. */
  private static readonly MAX_ALIAS_DEPTH = 8;

  // ─── initialisation ─────────────────────────────────────────────────────

  /** Loads the pack for the current era. Safe to await more than once. */
  async init(): Promise<void> {
    if (this.initPromise) return this.initPromise;
    this.initPromise = this.loadEra(this.era);
    return this.initPromise;
  }

  isReady(): boolean {
    return this.ready;
  }

  /** True once a pack with at least one sprite has loaded. */
  hasSprites(): boolean {
    return Object.keys(this.manifest.sprites).length > 0;
  }

  /** The composer, which turns a figure string into ordered layers. */
  get composer(): AvatarComposer {
    return this.composerInstance;
  }

  get figures(): FigureData {
    return this.figureData;
  }

  // ─── era ────────────────────────────────────────────────────────────────

  currentEra(): AssetEra {
    return this.era;
  }

  /**
   * Switches visual era and reloads the pack in place.
   *
   * Everything derived from the old pack is dropped: textures are keyed by URL
   * and the eras share sprite names, so keeping the cache would draw the
   * previous era's artwork under the new era's names.
   */
  async setEra(era: AssetEra): Promise<void> {
    if (era === this.era && this.ready) return;

    this.era = era;
    this.initPromise = this.loadEra(era);
    await this.initPromise;

    for (const listener of this.eraListeners) {
      try {
        listener(era);
      } catch {
        // One bad listener must not stop the others from redrawing.
      }
    }
  }

  /** Subscribes to era changes. Returns an unsubscribe function. */
  onEraChange(listener: EraListener): () => void {
    this.eraListeners.add(listener);
    return () => this.eraListeners.delete(listener);
  }

  private async loadEra(era: AssetEra): Promise<void> {
    this.ready = false;
    this.clearCaches();

    const root = `${this.baseUrl}/${era}`;
    await Promise.allSettled([
      this.loadManifest(root),
      this.loadFurnidata(root),
      this.loadFigureData(root),
    ]);

    this.ready = true;
  }

  private clearCaches(): void {
    for (const texture of this.textureCache.values()) {
      texture.destroy(true);
    }
    this.textureCache.clear();
    this.furnidata.clear();
    this.manifest = { sprites: {} };
    this.figureData = new FigureData();
    this.composerInstance = new AvatarComposer(this.figureData);
  }

  // ─── pack loading ───────────────────────────────────────────────────────

  private async loadManifest(root: string): Promise<void> {
    const manifest = await fetchJson<SpriteManifest>(`${root}/manifest.json`);
    if (manifest?.sprites) this.manifest = manifest;
  }

  private async loadFurnidata(root: string): Promise<void> {
    const xml = await fetchText(`${root}/furnidata.xml`);
    if (xml) this.parseFurnidata(xml);
  }

  private async loadFigureData(root: string): Promise<void> {
    const [figureData, figureMap] = await Promise.all([
      fetchText(`${root}/figuredata.xml`),
      fetchText(`${root}/figuremap.xml`),
    ]);
    if (figureData) this.figureData.parseFigureData(figureData);
    if (figureMap) this.figureData.parseFigureMap(figureMap);
  }

  private parseFurnidata(xml: string): void {
    const doc = new DOMParser().parseFromString(xml, 'text/xml');
    const read = (selector: string, type: 'S' | 'I') => {
      doc.querySelectorAll(`${selector} furnitype`).forEach((el) => {
        const id = el.getAttribute('classname')?.trim()
          ?? el.querySelector('id')?.textContent?.trim()
          ?? '';
        if (!id) return;
        this.furnidata.set(id, {
          id,
          type,
          revision: intOf(el.querySelector('revision')?.textContent, 0),
          xdim: intOf(el.querySelector('xdim')?.textContent, 1),
          ydim: intOf(el.querySelector('ydim')?.textContent, 1),
          name: el.querySelector('name')?.textContent?.trim() ?? id,
          description: el.querySelector('description')?.textContent?.trim() ?? '',
          canSitOn: el.querySelector('cansiton')?.textContent === '1',
          canStandOn: el.querySelector('canstandon')?.textContent === '1',
          canLayOn: el.querySelector('canlayon')?.textContent === '1',
          defaultDir: intOf(el.querySelector('defaultdir')?.textContent, 2),
        });
      });
    };
    read('roomitemtypes', 'S');
    read('wallitemtypes', 'I');
  }

  // ─── sprite resolution ──────────────────────────────────────────────────

  /** True if the pack knows this sprite, whether directly or as an alias. */
  hasSprite(name: string): boolean {
    return name in this.manifest.sprites;
  }

  /**
   * Resolves a sprite name to a texture, its draw offset, and whether it must
   * be mirrored.
   *
   * A pack ships only half the directions and derives the rest by mirroring, so
   * an entry may name another sprite instead of carrying an image. Those chains
   * are followed here, with a depth limit so a pack that points a sprite back at
   * itself cannot hang the renderer.
   */
  async resolve(name: string): Promise<ResolvedSprite | null> {
    let entry = this.manifest.sprites[name];
    if (!entry) return null;

    let flip = entry.flipH ?? false;
    const offsetX = entry.offsetX ?? 0;
    const offsetY = entry.offsetY ?? 0;

    let depth = 0;
    while (!entry.file && entry.source) {
      if (++depth > AssetLoaderImpl.MAX_ALIAS_DEPTH) return null;
      const next = this.manifest.sprites[entry.source];
      if (!next) return null;
      entry = next;
      // Mirroring twice returns to the original orientation.
      if (entry.flipH) flip = !flip;
    }

    if (!entry.file) return null;

    const texture = await this.loadTexture(`${this.baseUrl}/${this.era}/${entry.file}`);
    if (!texture) return null;

    return { texture, offsetX, offsetY, flip };
  }

  // ─── furniture ──────────────────────────────────────────────────────────

  /**
   * Sprite name for a furniture layer.
   * Convention: {item}_{size}_{layer}_{direction}_{frame}
   */
  furniSpriteName(item: string, direction: number, layer: string, frame: number, size = 64): string {
    return `${artworkOf(item)}_${size}_${layer}_${direction}_${frame}`;
  }

  async getFurniSprite(
    item: string,
    direction: number,
    layer = 'a',
    frame = 0,
    size = 64,
  ): Promise<ResolvedSprite | null> {
    return this.resolve(this.furniSpriteName(item, direction, layer, frame, size));
  }

  /** Layer letters present for an item and direction, in draw order. */
  furniLayers(item: string, direction: number, size = 64): string[] {
    const letters = 'abcdefghijklmnopqrstuvwxyz';
    const found: string[] = [];
    for (const letter of letters) {
      if (!this.hasSprite(this.furniSpriteName(item, direction, letter, 0, size))) break;
      found.push(letter);
    }
    return found;
  }

  /** Animation frame count for one layer; at least 1. */
  furniFrameCount(item: string, direction: number, layer: string, size = 64): number {
    let n = 0;
    while (this.hasSprite(this.furniSpriteName(item, direction, layer, n, size))) n++;
    return Math.max(1, n);
  }

  async getFurniIcon(item: string): Promise<ResolvedSprite | null> {
    for (const suffix of ['_icon', '_icon_a', '_icon_a_0_0']) {
      const resolved = await this.resolve(artworkOf(item) + suffix);
      if (resolved) return resolved;
    }
    return null;
  }

  getFurniInfo(item: string): FurniInfo | undefined {
    return this.furnidata.get(item);
  }

  /** Warms the cache for an item in all four placeable directions. */
  async prefetchFurni(item: string): Promise<void> {
    const layers = this.furniLayers(item, 0);
    if (layers.length === 0) return;
    await Promise.allSettled(
      [0, 2, 4, 6].flatMap((d) => layers.map((l) => this.getFurniSprite(item, d, l))),
    );
  }

  // ─── textures ───────────────────────────────────────────────────────────

  private async loadTexture(url: string): Promise<PIXI.Texture | null> {
    const cached = this.textureCache.get(url);
    if (cached) return cached;
    try {
      const texture = await PIXI.Assets.load<PIXI.Texture>(url);
      this.textureCache.set(url, texture);
      return texture;
    } catch {
      // A pack missing one sprite should cost that sprite, not the room.
      return null;
    }
  }
}

// ─── helpers ─────────────────────────────────────────────────────────────────

async function fetchText(url: string): Promise<string | null> {
  try {
    const res = await fetch(url);
    return res.ok ? await res.text() : null;
  } catch {
    return null;
  }
}

async function fetchJson<T>(url: string): Promise<T | null> {
  try {
    const res = await fetch(url);
    return res.ok ? ((await res.json()) as T) : null;
  } catch {
    return null;
  }
}

function intOf(text: string | null | undefined, fallback: number): number {
  const value = parseInt(text ?? '', 10);
  return Number.isNaN(value) ? fallback : value;
}

export const AssetLoader = new AssetLoaderImpl();
