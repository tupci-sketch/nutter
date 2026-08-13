import * as PIXI from 'pixi.js';
import { toScreen, TILE_W, TILE_H, FLOOR_COLOUR, FLOOR_DARK, WALL_COLOUR } from './iso';
import type { RoomUser, RoomFurni } from '@/stores/roomStore';

interface TileMap {
  width: number;
  height: number;
  tiles: number[][];
}

export class RoomRenderer {
  private readonly app: PIXI.Application;
  private readonly stage: PIXI.Container;
  private readonly floorLayer: PIXI.Container;
  private readonly furniLayer: PIXI.Container;
  private readonly avatarLayer: PIXI.Container;

  private tileMap: TileMap = { width: 0, height: 0, tiles: [] };
  private avatarGfx = new Map<number, PIXI.Graphics>();
  private furniGfx  = new Map<number, PIXI.Graphics>();

  constructor(canvas: HTMLCanvasElement, width: number, height: number) {
    this.app = new PIXI.Application({
      view: canvas,
      width,
      height,
      backgroundColor: 0x1a1a2e,
      resolution: window.devicePixelRatio || 1,
      autoDensity: true,
      antialias: false,
    });

    this.stage      = this.app.stage;
    this.floorLayer = new PIXI.Container();
    this.furniLayer = new PIXI.Container();
    this.avatarLayer= new PIXI.Container();

    this.stage.addChild(this.floorLayer);
    this.stage.addChild(this.furniLayer);
    this.stage.addChild(this.avatarLayer);
  }

  setTileMap(model: TileMap): void {
    this.tileMap = model;
    this.floorLayer.removeChildren();
    this.drawFloor();
  }

  private drawFloor(): void {
    const g = new PIXI.Graphics();
    const offsetX = this.app.view.width / 2;
    const offsetY = this.app.view.height / 4;

    for (let ty = 0; ty < this.tileMap.height; ty++) {
      for (let tx = 0; tx < this.tileMap.width; tx++) {
        const cell = this.tileMap.tiles[ty]?.[tx] ?? -1;
        if (cell < 0) continue;
        const { x, y } = toScreen(tx, ty, cell);
        this.drawTile(g, offsetX + x, offsetY + y);
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

    g.lineStyle(1, FLOOR_DARK, 0.5);
    g.moveTo(x,      y - hh);
    g.lineTo(x + hw, y);
    g.lineTo(x,      y + hh);
    g.lineTo(x - hw, y);
    g.closePath();
    g.lineStyle(0);
  }

  updateUsers(users: Map<number, RoomUser>): void {
    const offsetX = this.app.view.width / 2;
    const offsetY = this.app.view.height / 4;

    const seen = new Set<number>();
    users.forEach((user) => {
      seen.add(user.userId);
      let gfx = this.avatarGfx.get(user.userId);
      if (!gfx) {
        gfx = this.buildAvatar(user.username);
        this.avatarGfx.set(user.userId, gfx);
        this.avatarLayer.addChild(gfx);
      }
      const { x, y } = toScreen(user.x, user.y, user.z);
      gfx.x = offsetX + x;
      gfx.y = offsetY + y;
    });

    this.avatarGfx.forEach((gfx, uid) => {
      if (!seen.has(uid)) {
        this.avatarLayer.removeChild(gfx);
        this.avatarGfx.delete(uid);
      }
    });
  }

  private buildAvatar(username: string): PIXI.Graphics {
    const g = new PIXI.Graphics();
    g.beginFill(0xd4a76a);
    g.drawRoundedRect(-10, -40, 20, 20, 3);
    g.endFill();
    g.beginFill(0x4a7fc1);
    g.drawRect(-10, -20, 20, 24);
    g.endFill();

    const label = new PIXI.Text(username, {
      fontSize: 10,
      fill: 0xffffff,
      fontFamily: 'monospace',
    });
    label.anchor.set(0.5, 1);
    label.y = -42;
    g.addChild(label);
    return g;
  }

  updateFurni(furni: Map<number, RoomFurni>): void {
    const offsetX = this.app.view.width / 2;
    const offsetY = this.app.view.height / 4;

    const seen = new Set<number>();
    furni.forEach((f) => {
      seen.add(f.id);
      let gfx = this.furniGfx.get(f.id);
      if (!gfx) {
        gfx = this.buildFurni(f);
        this.furniGfx.set(f.id, gfx);
        this.furniLayer.addChild(gfx);
      }
      const { x, y } = toScreen(f.x, f.y, f.z);
      gfx.x = offsetX + x;
      gfx.y = offsetY + y;
    });

    this.furniGfx.forEach((gfx, fid) => {
      if (!seen.has(fid)) {
        this.furniLayer.removeChild(gfx);
        this.furniGfx.delete(fid);
      }
    });
  }

  private buildFurni(f: RoomFurni): PIXI.Graphics {
    const g = new PIXI.Graphics();
    const hw = TILE_W / 2 - 4;
    const hh = TILE_H / 2 - 2;
    g.beginFill(WALL_COLOUR);
    g.moveTo(0, -hh - 16);
    g.lineTo(hw, 0 - 16);
    g.lineTo(0, hh - 16);
    g.lineTo(-hw, 0 - 16);
    g.closePath();
    g.endFill();
    return g;
  }

  resize(width: number, height: number): void {
    this.app.renderer.resize(width, height);
    this.floorLayer.removeChildren();
    this.drawFloor();
  }

  destroy(): void {
    this.app.destroy(false);
  }

  onClick(handler: (tx: number, ty: number) => void): () => void {
    const listener = (ev: PIXI.InteractionEvent) => {
      const local = ev.data.getLocalPosition(this.floorLayer);
      const offsetX = this.app.view.width / 2;
      const offsetY = this.app.view.height / 4;
      const rx = local.x - offsetX;
      const ry = local.y - offsetY;
      const tx = Math.round((rx / (TILE_W / 2) + ry / (TILE_H / 2)) / 2);
      const ty = Math.round((ry / (TILE_H / 2) - rx / (TILE_W / 2)) / 2);
      handler(tx, ty);
    };
    this.stage.interactive = true;
    this.stage.on('pointerdown', listener);
    return () => this.stage.off('pointerdown', listener);
  }
}
