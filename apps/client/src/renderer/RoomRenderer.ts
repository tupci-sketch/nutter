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
    this.furniMap.forEach(s => s.destroy({ children: true }));
    this.avatarMap.forEach(f => f.destroy({ children: true }));
    this.app.destroy(false);
  }

  // ─── interaction ─────────────────────────────────────────────────────────

  /** Returns an unsubscribe function. */
  onClick(handler: (tx: number, ty: number) => void): () => void {
    const listener = (ev: PIXI.FederatedPointerEvent) => {
      const local = ev.getLocalPosition(this.floorLayer);
      const rx = local.x - this.offsetX;
      const ry = local.y - this.offsetY;
      const tx = Math.round((rx / (TILE_W / 2) + ry / (TILE_H / 2)) / 2);
      const ty = Math.round((ry / (TILE_H / 2) - rx / (TILE_W / 2)) / 2);
      handler(tx, ty);
    };
    this.stage.interactive = true;
    this.stage.on('pointerdown', listener);
    return () => this.stage.off('pointerdown', listener);
  }
}
