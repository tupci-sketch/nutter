import * as PIXI from 'pixi.js';
import { toScreen, TILE_W, TILE_H, FLOOR_COLOUR, FLOOR_DARK, WALL_COLOUR } from './iso';
import { AssetLoader } from './AssetLoader';
import { FurniSprite } from './FurniSprite';
import { AvatarFigure } from './AvatarFigure';
import type { RoomUser, RoomFurni } from '@/stores/roomStore';

interface TileMap {
  width: number;
  height: number;
  tiles: number[][]; // -1 = void, 0 = floor, >0 = elevated floor (height)
}

export class RoomRenderer {
  private readonly app: PIXI.Application;
  private readonly stage: PIXI.Container;
  private readonly floorLayer: PIXI.Container;
  private readonly furniLayer: PIXI.Container;
  private readonly avatarLayer: PIXI.Container;

  private tileMap: TileMap = { width: 0, height: 0, tiles: [] };
  private avatarMap = new Map<number, AvatarFigure>();
  private furniMap  = new Map<number, FurniSprite>();

  // Pixel offsets that centre the room in the canvas.
  private offsetX = 0;
  private offsetY = 0;

  // The last state pushed in, kept so the room can be rebuilt from scratch when
  // the artwork changes underneath it.
  private lastUsers: Map<number, RoomUser> = new Map();
  private lastFurni: Map<number, RoomFurni> = new Map();

  private stopEraWatch: (() => void) | null = null;

  constructor(canvas: HTMLCanvasElement, width: number, height: number) {
    this.app = new PIXI.Application({
      view:            canvas,
      width,
      height,
      backgroundColor: 0x1a1a2e,
      resolution:      window.devicePixelRatio || 1,
      autoDensity:     true,
      antialias:       false,
    });

    this.stage       = this.app.stage;
    this.floorLayer  = new PIXI.Container();
    this.furniLayer  = new PIXI.Container();
    this.avatarLayer = new PIXI.Container();

    this.stage.addChild(this.floorLayer);
    this.stage.addChild(this.furniLayer);
    this.stage.addChild(this.avatarLayer);

    // Kick off asset loading; room will upgrade automatically once ready.
    void AssetLoader.init();

    // Switching visual era replaces every texture in the pack, so each sprite
    // has to be rebuilt from the state we last received. The room, the world
    // and everyone in it stay exactly as they were.
    this.stopEraWatch = AssetLoader.onEraChange(() => this.rebuildAll());
  }

  /** Rebuilds every sprite from the last known state, keeping positions. */
  private rebuildAll(): void {
    this.furniMap.forEach((sprite) => sprite.destroy({ children: true }));
    this.furniMap.clear();
    this.avatarMap.forEach((figure) => figure.destroy({ children: true }));
    this.avatarMap.clear();

    this.floorLayer.removeChildren();
    this.drawFloor();

    this.updateFurni(this.lastFurni);
    this.updateUsers(this.lastUsers);
  }

  // ─── tile map ────────────────────────────────────────────────────────────

  setTileMap(model: TileMap): void {
    this.tileMap = model;
    this.offsetX = this.app.view.width  / 2;
    this.offsetY = this.app.view.height / 4;
    this.floorLayer.removeChildren();
    this.drawFloor();
  }

  private drawFloor(): void {
    const g = new PIXI.Graphics();

    for (let ty = 0; ty < this.tileMap.height; ty++) {
      for (let tx = 0; tx < this.tileMap.width; tx++) {
        const cell = this.tileMap.tiles[ty]?.[tx] ?? -1;
        if (cell < 0) continue;
        const { x, y } = toScreen(tx, ty, cell);
        this.drawTile(g, this.offsetX + x, this.offsetY + y);
        // Draw left wall panel for the first column.
        if (tx === 0) this.drawWallLeft(g, this.offsetX + x, this.offsetY + y);
        // Draw back wall panel for the first row.
        if (ty === 0) this.drawWallBack(g, this.offsetX + x, this.offsetY + y);
      }
    }
    this.floorLayer.addChild(g);
  }

  private drawTile(g: PIXI.Graphics, x: number, y: number): void {
    const hw = TILE_W / 2;
    const hh = TILE_H / 2;

    g.beginFill(FLOOR_COLOUR);
    g.moveTo(x,      y - hh);
    g.lineTo(x + hw, y);
    g.lineTo(x,      y + hh);
    g.lineTo(x - hw, y);
    g.closePath();
    g.endFill();

    g.lineStyle(1, FLOOR_DARK, 0.4);
    g.moveTo(x,      y - hh);
    g.lineTo(x + hw, y);
    g.lineTo(x,      y + hh);
    g.lineTo(x - hw, y);
    g.closePath();
    g.lineStyle(0);
  }

  private drawWallLeft(g: PIXI.Graphics, x: number, y: number): void {
    const hw = TILE_W / 2;
    const hh = TILE_H / 2;
    const wallH = 64;

    g.beginFill(WALL_COLOUR, 0.9);
    g.moveTo(x - hw, y);
    g.lineTo(x,      y + hh);
    g.lineTo(x,      y + hh - wallH);
    g.lineTo(x - hw, y - wallH);
    g.closePath();
    g.endFill();
  }

  private drawWallBack(g: PIXI.Graphics, x: number, y: number): void {
    const hw = TILE_W / 2;
    const hh = TILE_H / 2;
    const wallH = 64;

    g.beginFill(0xa09080, 0.9); // slightly different shade for back wall
    g.moveTo(x,      y - hh);
    g.lineTo(x + hw, y);
    g.lineTo(x + hw, y - wallH);
    g.lineTo(x,      y - hh - wallH);
    g.closePath();
    g.endFill();
  }

  // ─── furniture ───────────────────────────────────────────────────────────

  updateFurni(furni: Map<number, RoomFurni>): void {
    this.lastFurni = furni;
    const seen = new Set<number>();

    furni.forEach(f => {
      seen.add(f.id);
      let sprite = this.furniMap.get(f.id);

      if (!sprite) {
        sprite = new FurniSprite(f);
        this.furniMap.set(f.id, sprite);
        this.furniLayer.addChild(sprite);
      } else {
        sprite.update(f);
      }

      const { x, y } = toScreen(f.x, f.y, f.z);
      sprite.x = this.offsetX + x;
      sprite.y = this.offsetY + y;
    });

    this.furniMap.forEach((sprite, id) => {
      if (!seen.has(id)) {
        sprite.destroy({ children: true });
        this.furniMap.delete(id);
      }
    });

    this.depthSortFurni();
  }

  /** Depth-sort furniture by tile position so overlapping items render correctly. */
  private depthSortFurni(): void {
    const children = [...this.furniLayer.children] as FurniSprite[];
    children.sort((a, b) => (a.y + a.x) - (b.y + b.x));
    children.forEach((c, i) => { this.furniLayer.setChildIndex(c, i); });
  }

  // ─── avatars ─────────────────────────────────────────────────────────────

  updateUsers(users: Map<number, RoomUser>): void {
    this.lastUsers = users;
    const seen = new Set<number>();

    users.forEach(user => {
      seen.add(user.userId);
      let figure = this.avatarMap.get(user.userId);

      if (!figure) {
        figure = new AvatarFigure(user);
        this.avatarMap.set(user.userId, figure);
        this.avatarLayer.addChild(figure);
      } else {
        figure.update(user);
      }

      const { x, y } = toScreen(user.x, user.y, user.z);
      figure.x = this.offsetX + x;
      figure.y = this.offsetY + y;
    });

    this.avatarMap.forEach((fig, uid) => {
      if (!seen.has(uid)) {
        fig.destroy({ children: true });
        this.avatarMap.delete(uid);
      }
    });

    this.depthSortAvatars();
  }

  private depthSortAvatars(): void {
    const children = [...this.avatarLayer.children] as AvatarFigure[];
    children.sort((a, b) => (a.y + a.x) - (b.y + b.x));
    children.forEach((c, i) => { this.avatarLayer.setChildIndex(c, i); });
  }

  // ─── resize / destroy ────────────────────────────────────────────────────

  resize(width: number, height: number): void {
    this.app.renderer.resize(width, height);
    this.floorLayer.removeChildren();
    this.offsetX = width  / 2;
    this.offsetY = height / 4;
    this.drawFloor();
  }

  destroy(): void {
    this.stopEraWatch?.();
    this.stopEraWatch = null;
    this.furniMap.forEach(s => s.destroy({ children: true }));
    this.avatarMap.forEach(f => f.destroy({ children: true }));
    this.app.destroy(false);
  }

  // ─── interaction ─────────────────────────────────────────────────────────

  /** Returns an unsubscribe function. */
  onClick(handler: (tx: number, ty: number) => void): () => void {
    const listener = (ev: PIXI.FederatedPointerEvent) => {
      // A tap that was really the end of a drag or a pinch is not a tap: it
      // would walk the avatar every time somebody moved the camera.
      if (this.gestureMoved) return;

      const local = ev.getLocalPosition(this.floorLayer);
      handler(...this.tileAt(local.x, local.y));
    };
    this.stage.interactive = true;
    this.stage.on('pointerup', listener);
    return () => this.stage.off('pointerup', listener);
  }

  /** The tile under a point in the floor layer's own coordinates. */
  private tileAt(x: number, y: number): [number, number] {
    const rx = x - this.offsetX;
    const ry = y - this.offsetY;
    return [
      Math.round((rx / (TILE_W / 2) + ry / (TILE_H / 2)) / 2),
      Math.round((ry / (TILE_H / 2) - rx / (TILE_W / 2)) / 2),
    ];
  }

  // ─── camera ──────────────────────────────────────────────────────────────

  /** How far the view may be zoomed, either way. */
  private static readonly MIN_ZOOM = 0.5;

  private static readonly MAX_ZOOM = 3;

  private zoom = 1;

  /** True once the current gesture has moved far enough not to be a tap. */
  private gestureMoved = false;

  /** Sets the zoom, clamped to what is useful. */
  setZoom(zoom: number): void {
    this.zoom = Math.max(RoomRenderer.MIN_ZOOM, Math.min(RoomRenderer.MAX_ZOOM, zoom));
    this.stage.scale.set(this.zoom);
  }

  getZoom(): number {
    return this.zoom;
  }

  /** Multiplies the current zoom, for a pinch or a scroll wheel. */
  zoomBy(factor: number): void {
    this.setZoom(this.zoom * factor);
  }

  /** Moves the view by a number of screen pixels. */
  panBy(dx: number, dy: number): void {
    this.stage.position.set(this.stage.position.x + dx, this.stage.position.y + dy);
  }

  /** Puts the camera back where it started. */
  resetCamera(): void {
    this.setZoom(1);
    this.stage.position.set(0, 0);
  }

  /**
   * Marks the current gesture as a drag rather than a tap.
   *
   * Held here rather than in the component because the click handler above is
   * the thing that has to know, and it runs inside the renderer.
   */
  setGestureMoved(moved: boolean): void {
    this.gestureMoved = moved;
  }
}
