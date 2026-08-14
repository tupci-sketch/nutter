import * as PIXI from 'pixi.js';

// ─── types ───────────────────────────────────────────────────────────────────

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

export interface FigureLibrary {
  id: string;
  revision: number;
  parts: FigurePart[];
}

export interface FigurePart {
  id: string;
  type: string;
  colorable: boolean;
  index: number;
  colorIndex: number;
}

interface SpriteEntry {
  file: string;
  width: number;
  height: number;
}

interface SpriteManifest {
  sprites: Record<string, SpriteEntry>;
}

// ─── loader ──────────────────────────────────────────────────────────────────

class AssetLoaderImpl {
  private baseUrl = '/assets';
  private textureCache = new Map<string, PIXI.Texture>();
  private furnidata = new Map<string, FurniInfo>();
  private figureLibs = new Map<string, FigureLibrary>();
  private manifest: SpriteManifest = { sprites: {} };
  private ready = false;
  private initPromise: Promise<void> | null = null;

  // ─── initialisation ─────────────────────────────────────────────────────

  /** Must be called once before the room is rendered. */
  async init(): Promise<void> {
    if (this.initPromise) return this.initPromise;
    this.initPromise = this._init();
    return this.initPromise;
  }

  private async _init(): Promise<void> {
    await Promise.allSettled([
      this.loadManifest(),
      this.loadFurnidata(),
      this.loadFiguremap(),
    ]);
    this.ready = true;
  }

  /** True once init() has resolved. */
  isReady(): boolean {
    return this.ready;
  }

  /** True if at least one sprite was found in the manifest. */
  hasSprites(): boolean {
    return Object.keys(this.manifest.sprites).length > 0;
  }

  // ─── manifest / XML loaders ─────────────────────────────────────────────

  private async loadManifest(): Promise<void> {
    try {
      const res = await fetch(`${this.baseUrl}/manifest.json`);
      if (!res.ok) return;
      this.manifest = await res.json();
    } catch {
      // Assets not yet installed — will use placeholder rendering.
    }
  }

  private async loadFurnidata(): Promise<void> {
    try {
      const res = await fetch(`${this.baseUrl}/furnidata.xml`);
      if (!res.ok) return;
      const text = await res.text();
      this.parseFurnidata(text);
    } catch { /* ignore */ }
  }

  private parseFurnidata(xml: string): void {
    const doc = new DOMParser().parseFromString(xml, 'text/xml');
    const process = (selector: string, type: 'S' | 'I') => {
      doc.querySelectorAll(selector + ' itemtype').forEach(el => {
        const id = el.querySelector('id')?.textContent?.trim() ?? '';
        if (!id) return;
        this.furnidata.set(id, {
          id,
          type,
          revision:    parseInt(el.querySelector('revision')?.textContent ?? '0'),
          xdim:        parseInt(el.querySelector('xdim')?.textContent ?? '1'),
          ydim:        parseInt(el.querySelector('ydim')?.textContent ?? '1'),
          name:        el.querySelector('name')?.textContent?.trim() ?? id,
          description: el.querySelector('description')?.textContent?.trim() ?? '',
          canSitOn:    el.querySelector('cansiton')?.textContent === '1',
          canStandOn:  el.querySelector('canstandon')?.textContent === '1',
          canLayOn:    el.querySelector('canlayon')?.textContent === '1',
          defaultDir:  parseInt(el.querySelector('defaultdir')?.textContent ?? '2'),
        });
      });
    };
    process('roomitemtypes', 'S');
    process('wallitemtypes', 'I');
  }

  private async loadFiguremap(): Promise<void> {
    try {
      const res = await fetch(`${this.baseUrl}/figuremap.xml`);
      if (!res.ok) return;
      const text = await res.text();
      this.parseFiguremap(text);
    } catch { /* ignore */ }
  }

  private parseFiguremap(xml: string): void {
    const doc = new DOMParser().parseFromString(xml, 'text/xml');
    doc.querySelectorAll('map > lib').forEach(lib => {
      const id = lib.getAttribute('id') ?? '';
      const rev = parseInt(lib.getAttribute('revision') ?? '0');
      const parts: FigurePart[] = [];
      lib.querySelectorAll('part').forEach(p => {
        parts.push({
          id:         p.getAttribute('id') ?? '0',
          type:       p.getAttribute('type') ?? '',
          colorable:  p.getAttribute('colorable') === '1',
          index:      parseInt(p.getAttribute('index') ?? '0'),
          colorIndex: parseInt(p.getAttribute('colorindex') ?? '1'),
        });
      });
      this.figureLibs.set(id, { id, revision: rev, parts });
    });
  }

  // ─── furni texture lookups ───────────────────────────────────────────────

  /**
   * Returns the PIXI.Texture for a furniture sprite, or null if not available.
   * Sprite name convention: {baseItem}_{size}_{layer}_{dir}_{frame}
   * e.g. "throne_64_a_0_0"
   */
  async getFurniTexture(
    baseItem: string,
    direction: number,
    layer = 'a',
    frame = 0,
    size = 64,
  ): Promise<PIXI.Texture | null> {
    const name = `${baseItem}_${size}_${layer}_${direction}_${frame}`;
    const entry = this.manifest.sprites[name];
    if (!entry) return null;
    return this.loadTexture(`${this.baseUrl}/${entry.file}`);
  }

  /** Returns all available layer letters for a furniture item+direction. */
  furniLayers(baseItem: string, direction: number, size = 64): string[] {
    const letters = 'abcdefghijklmnopqrstuvwxyz';
    const result: string[] = [];
    for (const l of letters) {
      const name = `${baseItem}_${size}_${l}_${direction}_0`;
      if (this.manifest.sprites[name]) {
        result.push(l);
      } else {
        break;
      }
    }
    return result;
  }

  /** Returns the number of animation frames for a given furniture layer. */
  furniFrameCount(baseItem: string, direction: number, layer: string, size = 64): number {
    let n = 0;
    while (this.manifest.sprites[`${baseItem}_${size}_${layer}_${direction}_${n}`]) n++;
    return Math.max(1, n);
  }

  /** Catalog/inventory icon for a furniture item. */
  async getFurniIcon(baseItem: string): Promise<PIXI.Texture | null> {
    for (const suffix of ['_icon', '_icon_a_0_0']) {
      const name = baseItem + suffix;
      if (this.manifest.sprites[name]) {
        const entry = this.manifest.sprites[name];
        return this.loadTexture(`${this.baseUrl}/${entry.file}`);
      }
    }
    return null;
  }

  // ─── figure texture lookups ──────────────────────────────────────────────

  /**
   * Returns a PIXI.Texture for an avatar body part, or null if not available.
   * Sprite name convention: {libId}_{type}_{dir}_{frame}
   * e.g. "hh_human_body_0_0"
   */
  async getAvatarPartTexture(
    libId: string,
    partType: string,
    direction: number,
    frame = 0,
  ): Promise<PIXI.Texture | null> {
    const name = `${libId}_${partType}_${direction}_${frame}`;
    const entry = this.manifest.sprites[name];
    if (!entry) return null;
    return this.loadTexture(`${this.baseUrl}/${entry.file}`);
  }

  /** Returns the FigureLibrary that owns a given part type, if any. */
  figureLibForPartType(partType: string): FigureLibrary | undefined {
    for (const lib of this.figureLibs.values()) {
      if (lib.parts.some(p => p.type === partType)) return lib;
    }
    return undefined;
  }

  /** Furni metadata — name, dims, interaction flags. */
  getFurniInfo(baseItem: string): FurniInfo | undefined {
    return this.furnidata.get(baseItem);
  }

  // ─── texture cache ───────────────────────────────────────────────────────

  private async loadTexture(url: string): Promise<PIXI.Texture | null> {
    if (this.textureCache.has(url)) {
      return this.textureCache.get(url)!;
    }
    try {
      const texture = await PIXI.Assets.load<PIXI.Texture>(url);
      this.textureCache.set(url, texture);
      return texture;
    } catch {
      return null;
    }
  }

  /** Pre-warm the texture cache for a furniture item in all 4 directions. */
  async prefetchFurni(baseItem: string): Promise<void> {
    const dirs = [0, 2, 4, 6];
    const layers = this.furniLayers(baseItem, 0);
    if (layers.length === 0) return;
    await Promise.allSettled(
      dirs.flatMap(d => layers.map(l => this.getFurniTexture(baseItem, d, l)))
    );
  }
}

export const AssetLoader = new AssetLoaderImpl();
