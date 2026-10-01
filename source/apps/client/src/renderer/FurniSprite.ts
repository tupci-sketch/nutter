import * as PIXI from 'pixi.js';
import { AssetLoader } from './AssetLoader';
import { TILE_W, TILE_H, WALL_COLOUR } from './iso';
import type { FloorItem } from '@/stores/roomStore';

// Direction cycle for rotate-right / rotate-left.
const DIR_CYCLE = [0, 2, 4, 6];

/**
 * FurniSprite renders a single piece of furniture.
 *
 * When SWF sprites are available it uses layered PIXI.Sprites loaded from the
 * asset server.  When they are not it falls back to the same coloured diamond
 * geometry the original RoomRenderer drew, so the room always looks reasonable
 * regardless of whether a SWF pack has been installed.
 */
export class FurniSprite extends PIXI.Container {
  private furni: FloorItem;
  private layerSprites: PIXI.Sprite[] = [];
  private frameTimer: ReturnType<typeof setInterval> | null = null;
  private currentFrame = 0;
  private frameCount = 1;
  private size = 64;

  constructor(furni: FloorItem) {
    super();
    this.furni = furni;
    this.sortableChildren = false;
    this.rebuild();
  }

  // ─── public API ───────────────────────────────────────────────────────────

  update(furni: FloorItem): void {
    const dirChanged   = furni.rotation !== this.furni.rotation;
    const stateChanged = furni.state !== this.furni.state;
    this.furni = furni;
    if (dirChanged || stateChanged) this.rebuild();
  }

  setDirection(dir: number): void {
    if (this.furni.rotation === dir) return;
    this.furni = { ...this.furni, rotation: dir };
    this.rebuild();
  }

  rotateRight(): void {
    const i = DIR_CYCLE.indexOf(this.furni.rotation);
    this.setDirection(DIR_CYCLE[(i + 1) % DIR_CYCLE.length]);
  }

  destroy(options?: PIXI.IDestroyOptions | boolean): void {
    this.stopAnimation();
    super.destroy(options);
  }

  // ─── rendering ────────────────────────────────────────────────────────────

  private rebuild(): void {
    this.stopAnimation();
    this.removeChildren();
    this.layerSprites = [];

    if (AssetLoader.hasSprites()) {
      void this.buildFromSprites();
    } else {
      this.buildPlaceholder();
    }
  }

  private async buildFromSprites(): Promise<void> {
    const { spriteId: baseItem, rotation: dir } = this.furni;
    const layers = AssetLoader.furniLayers(baseItem, dir, this.size);

    if (layers.length === 0) {
      // The pack has no artwork for this item — fall back to the placeholder.
      this.buildPlaceholder();
      return;
    }

    this.frameCount = AssetLoader.furniFrameCount(baseItem, dir, layers[0], this.size);

    for (const layer of layers) {
      const resolved = await AssetLoader.getFurniSprite(
        baseItem, dir, layer, this.currentFrame, this.size,
      );
      if (!resolved) continue;

      const sprite = new PIXI.Sprite(resolved.texture);
      // The offset carried by the pack places the artwork against the tile
      // origin. Without it every piece floats off its own square.
      if (resolved.flip) {
        sprite.scale.x = -1;
        sprite.x = resolved.offsetX + resolved.texture.width;
      } else {
        sprite.x = -resolved.offsetX;
      }
      sprite.y = -resolved.offsetY;

      this.addChild(sprite);
      this.layerSprites.push(sprite);
    }

    if (this.layerSprites.length === 0) {
      this.buildPlaceholder();
    } else if (this.frameCount > 1) {
      this.startAnimation();
    }
  }

  private buildPlaceholder(): void {
    const g = new PIXI.Graphics();
    const hw = TILE_W / 2 - 4;
    const hh = TILE_H / 2 - 2;
    const lift = 16; // visual height above the floor

    // Top face
    g.beginFill(WALL_COLOUR, 0.9);
    g.moveTo(0,   -hh - lift);
    g.lineTo(hw,   0  - lift);
    g.lineTo(0,    hh - lift);
    g.lineTo(-hw,  0  - lift);
    g.closePath();
    g.endFill();

    // Left side
    g.beginFill(darken(WALL_COLOUR), 0.9);
    g.moveTo(-hw, 0 - lift);
    g.lineTo(0,   hh - lift);
    g.lineTo(0,   hh);
    g.lineTo(-hw, 0);
    g.closePath();
    g.endFill();

    // Right side
    g.beginFill(darken(WALL_COLOUR, 0.7), 0.9);
    g.moveTo(hw, 0 - lift);
    g.lineTo(0,  hh - lift);
    g.lineTo(0,  hh);
    g.lineTo(hw, 0);
    g.closePath();
    g.endFill();

    this.addChild(g);
  }

  // ─── animation ────────────────────────────────────────────────────────────

  private startAnimation(): void {
    this.frameTimer = setInterval(() => void this.advanceFrame(), 120);
  }

  private stopAnimation(): void {
    if (this.frameTimer !== null) {
      clearInterval(this.frameTimer);
      this.frameTimer = null;
    }
  }

  private async advanceFrame(): Promise<void> {
    this.currentFrame = (this.currentFrame + 1) % this.frameCount;
    const { spriteId: baseItem, rotation: dir } = this.furni;

    await Promise.all(
      this.layerSprites.map(async (sprite, i) => {
        const layer = String.fromCharCode(97 + i); // a, b, c, …
        const resolved = await AssetLoader.getFurniSprite(
          baseItem, dir, layer, this.currentFrame, this.size,
        );
        // The sprite may have been torn down while this frame was loading.
        if (resolved && !sprite.destroyed) {
          sprite.texture = resolved.texture;
        }
      }),
    );
  }
}

// ─── helpers ─────────────────────────────────────────────────────────────────

function darken(hex: number, factor = 0.8): number {
  const r = Math.floor(((hex >> 16) & 0xFF) * factor);
  const g = Math.floor(((hex >> 8)  & 0xFF) * factor);
  const b = Math.floor((hex         & 0xFF) * factor);
  return (r << 16) | (g << 8) | b;
}
