import * as PIXI from 'pixi.js';
import { AssetLoader } from './AssetLoader';
import { ACTION, type FigureAction } from './figure/AvatarComposer';
import type { RoomUser } from '@/stores/roomStore';

/**
 * One user, pet or bot drawn in a room.
 *
 * The figure is composed from the installed pack: the composer decides which
 * sprites to draw, in what order, tinted which colour and mirrored or not, and
 * this class turns that list into sprites. When no pack is installed it falls
 * back to a simple silhouette so a room still renders during setup.
 */
export class AvatarFigure extends PIXI.Container {
  private user: RoomUser;
  private partSprites: PIXI.Sprite[] = [];
  private label: PIXI.Text | null = null;

  private action: FigureAction = ACTION.Stand;
  private frame = 0;
  private frameTimer: ReturnType<typeof setInterval> | null = null;

  /** Guards against an out-of-order rebuild painting a stale figure. */
  private buildToken = 0;

  private static readonly WALK_FRAME_MS = 160;
  private static readonly WALK_FRAMES = 4;

  constructor(user: RoomUser) {
    super();
    this.user = user;
    this.sortableChildren = true;
    this.rebuild();
  }

  // ─── public API ───────────────────────────────────────────────────────────

  update(user: RoomUser): void {
    const previous = this.user;
    this.user = user;

    const moved = user.x !== previous.x || user.y !== previous.y;
    const changed =
      user.dir !== previous.dir ||
      user.figure !== previous.figure ||
      user.username !== previous.username;

    if (moved) {
      this.startWalking();
      return;
    }
    if (this.action === ACTION.Walk) this.stopWalking();
    if (changed) this.rebuild();
  }

  /** Plays a one-shot expression, returning to standing afterwards. */
  playAction(action: FigureAction, durationMs = 1200): void {
    this.action = action;
    this.rebuild();
    window.setTimeout(() => {
      if (this.action !== action) return;
      this.action = ACTION.Stand;
      this.rebuild();
    }, durationMs);
  }

  setSitting(sitting: boolean): void {
    const next = sitting ? ACTION.Sit : ACTION.Stand;
    if (next === this.action) return;
    this.action = next;
    this.rebuild();
  }

  destroy(options?: PIXI.IDestroyOptions | boolean): void {
    this.stopWalking();
    super.destroy(options);
  }

  // ─── rendering ────────────────────────────────────────────────────────────

  private rebuild(): void {
    const token = ++this.buildToken;
    this.removeChildren();
    this.partSprites = [];
    this.label = null;

    if (AssetLoader.hasSprites()) {
      void this.buildFromPack(token);
    } else {
      this.buildSilhouette();
      this.addUsernameLabel();
    }
  }

  private async buildFromPack(token: number): Promise<void> {
    const layers = AssetLoader.composer.compose({
      figure: this.user.figure ?? '',
      direction: this.user.dir ?? 2,
      action: this.action,
      frame: this.frame,
    });

    const resolved = await Promise.all(
      layers.map(async (layer) => ({ layer, sprite: await AssetLoader.resolve(layer.sprite) })),
    );

    // An await elsewhere may have started a newer build; that one owns the
    // container now.
    if (token !== this.buildToken) return;

    let drawn = 0;
    for (const { layer, sprite } of resolved) {
      if (!sprite) continue;

      const part = new PIXI.Sprite(sprite.texture);
      part.zIndex = layer.z;

      // Offsets place the part against the avatar's origin, which sits at the
      // feet. A mirrored part is flipped about its own width.
      if (layer.flip || sprite.flip) {
        part.scale.x = -1;
        part.x = sprite.offsetX + sprite.texture.width;
      } else {
        part.x = -sprite.offsetX;
      }
      part.y = -sprite.offsetY;

      if (layer.tint !== null) part.tint = layer.tint;

      this.addChild(part);
      this.partSprites.push(part);
      drawn++;
    }

    if (drawn === 0) this.buildSilhouette();
    this.addUsernameLabel();
  }

  /**
   * A neutral stand-in used before a pack is installed, or when the pack has
   * none of this figure's parts.
   */
  private buildSilhouette(): void {
    const g = new PIXI.Graphics();
    g.zIndex = 0;

    g.beginFill(0xd4a76a);
    g.drawRoundedRect(-10, -40, 20, 20, 3);
    g.endFill();

    g.beginFill(0x4a7fc1);
    g.drawRect(-10, -20, 20, 24);
    g.endFill();

    this.addChild(g);
  }

  private addUsernameLabel(): void {
    if (this.label) return;
    const label = new PIXI.Text(this.user.username, {
      fontSize: 10,
      fill: 0xffffff,
      fontFamily: 'monospace',
      stroke: 0x000000,
      strokeThickness: 3,
    });
    label.anchor.set(0.5, 1);
    label.y = -this.figureHeight() - 6;
    label.zIndex = 10_000;
    this.addChild(label);
    this.label = label;
  }

  /** Height of the drawn figure, used to place the name above it. */
  private figureHeight(): number {
    let top = 0;
    for (const sprite of this.partSprites) {
      top = Math.min(top, sprite.y - sprite.height);
    }
    return top === 0 ? 46 : Math.abs(top);
  }

  // ─── walk cycle ───────────────────────────────────────────────────────────

  private startWalking(): void {
    if (this.action === ACTION.Walk) {
      this.rebuild();
      return;
    }
    this.action = ACTION.Walk;
    this.frame = 0;
    this.rebuild();

    this.frameTimer ??= setInterval(() => {
      this.frame = (this.frame + 1) % AvatarFigure.WALK_FRAMES;
      this.rebuild();
    }, AvatarFigure.WALK_FRAME_MS);
  }

  private stopWalking(): void {
    if (this.frameTimer !== null) {
      clearInterval(this.frameTimer);
      this.frameTimer = null;
    }
    if (this.action === ACTION.Walk) {
      this.action = ACTION.Stand;
      this.frame = 0;
      this.rebuild();
    }
  }
}
