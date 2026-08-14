/**
 * White House room test harness.
 *
 * Exercises the real RoomRenderer and iso projection with a hand-crafted
 * "White House" tile map and furniture set, bypassing the WebSocket so the
 * renderer can be verified without a running emulator.
 */
import * as PIXI from 'pixi.js';
import { toScreen, TILE_W, TILE_H } from './renderer/iso';

// ─── constants ──────────────────────────────────────────────────────────────

const BG        = 0x0d0d1a;
const TILE_MAIN = 0xe8dcc8;   // cream marble
const TILE_DARK = 0xc8b89a;   // darker marble seam
const CARPET_A  = 0x8b1a1a;   // deep presidential red
const CARPET_B  = 0x6b1010;   // shadow red
const WALL_A    = 0xf0e8d8;   // white wall face
const WALL_B    = 0xd8cdb8;   // wall shadow
const COLUMN_A  = 0xfafaf5;   // column top
const COLUMN_B  = 0xe0d8c8;   // column side shadow
const GOLD      = 0xc8a832;   // trim gold
const BANNER_R  = 0x9b1a1a;   // banner red
const BANNER_B  = 0x1a2a7a;   // banner blue

// ─── tile map ──────────────────────────────────────────────────────────────
// 0=floor, 1=red carpet, -1=void, 2=carpet edge
// 14 columns × 11 rows
const MAP_W = 14;
const MAP_H = 11;

const TILES: number[][] = [
  [ 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0],
  [ 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0],
  [ 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0],
  [ 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0],
  [ 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0],
  [ 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0],
  [ 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0],
  [ 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0],
  [ 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0],
  [ 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0],
  [ 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0],
];

// ─── furniture definitions ──────────────────────────────────────────────────

interface Furni { tx: number; ty: number; kind: string; }

const FURNITURE: Furni[] = [
  // Left column row 1
  { tx: 1,  ty: 1,  kind: 'column' },
  { tx: 1,  ty: 4,  kind: 'column' },
  { tx: 1,  ty: 7,  kind: 'column' },
  // Right column row 1
  { tx: 12, ty: 1,  kind: 'column' },
  { tx: 12, ty: 4,  kind: 'column' },
  { tx: 12, ty: 7,  kind: 'column' },
  // Throne at head of room
  { tx: 6,  ty: 0,  kind: 'throne' },
  // Podium
  { tx: 7,  ty: 0,  kind: 'podium' },
  // Left banners
  { tx: 0,  ty: 0,  kind: 'banner' },
  { tx: 0,  ty: 3,  kind: 'banner' },
  { tx: 0,  ty: 6,  kind: 'banner' },
  // Right banners
  { tx: 13, ty: 0,  kind: 'banner' },
  { tx: 13, ty: 3,  kind: 'banner' },
  { tx: 13, ty: 6,  kind: 'banner' },
  // Side chairs left
  { tx: 3,  ty: 3,  kind: 'chair' },
  { tx: 3,  ty: 5,  kind: 'chair' },
  // Side chairs right
  { tx: 10, ty: 3,  kind: 'chair' },
  { tx: 10, ty: 5,  kind: 'chair' },
];

// ─── avatars ─────────────────────────────────────────────────────────────────

interface Avatar { tx: number; ty: number; username: string; isBot?: boolean; look: string; }

const AVATARS: Avatar[] = [
  { tx: 6, ty: 6, username: 'tupci',  look: 'admin',  isBot: false },
  { tx: 8, ty: 5, username: 'habbot', look: 'bot',    isBot: true  },
];

// ─── main ────────────────────────────────────────────────────────────────────

const canvas = document.getElementById('c') as HTMLCanvasElement;
const W = canvas.parentElement!.clientWidth;
const H = canvas.parentElement!.clientHeight;

const app = new PIXI.Application({
  view: canvas,
  width:  W,
  height: H,
  backgroundColor: BG,
  resolution: Math.min(window.devicePixelRatio || 1, 2),
  autoDensity: true,
  antialias: false,
});

const stage    = app.stage;
const floorGfx = new PIXI.Graphics();
const wallGfx  = new PIXI.Graphics();
const furniGfx = new PIXI.Graphics();
const avatarL  = new PIXI.Container();

stage.addChild(floorGfx);
stage.addChild(wallGfx);
stage.addChild(furniGfx);
stage.addChild(avatarL);

// centre the room
const OX = W / 2;
const OY = H / 5;

function sc(tx: number, ty: number, tz = 0) {
  const s = toScreen(tx, ty, tz);
  return { x: OX + s.x, y: OY + s.y };
}

// ── draw walls (back wall + left wall) ──────────────────────────────────────
function drawWalls() {
  const WH = 48; // wall height in px
  // Back wall (top edge of map, ty=0)
  for (let tx = 0; tx < MAP_W; tx++) {
    const { x, y } = sc(tx, 0);
    wallGfx.beginFill(WALL_A);
    wallGfx.moveTo(x,          y - TILE_H / 2);
    wallGfx.lineTo(x + TILE_W / 2, y);
    wallGfx.lineTo(x + TILE_W / 2, y - WH);
    wallGfx.lineTo(x,          y - TILE_H / 2 - WH);
    wallGfx.closePath();
    wallGfx.endFill();
    wallGfx.lineStyle(1, WALL_B, 0.6);
    wallGfx.moveTo(x, y - TILE_H / 2);
    wallGfx.lineTo(x + TILE_W / 2, y);
    wallGfx.lineStyle(0);
  }
  // Left wall (tx=0)
  for (let ty = 0; ty < MAP_H; ty++) {
    const { x, y } = sc(0, ty);
    wallGfx.beginFill(WALL_B);
    wallGfx.moveTo(x,          y - TILE_H / 2);
    wallGfx.lineTo(x,          y + TILE_H / 2);
    wallGfx.lineTo(x - TILE_W / 2, y);
    wallGfx.lineTo(x - TILE_W / 2, y - WH);
    wallGfx.closePath();
    wallGfx.endFill();
  }
}

// ── draw floor tiles ─────────────────────────────────────────────────────────
function drawFloor() {
  for (let ty = 0; ty < MAP_H; ty++) {
    for (let tx = 0; tx < MAP_W; tx++) {
      const cell = TILES[ty][tx] ?? -1;
      if (cell < 0) continue;
      const { x, y } = sc(tx, ty);
      const isRed = cell === 1;
      const hw = TILE_W / 2;
      const hh = TILE_H / 2;

      // top face
      floorGfx.beginFill(isRed ? CARPET_A : TILE_MAIN);
      floorGfx.moveTo(x,      y - hh);
      floorGfx.lineTo(x + hw, y);
      floorGfx.lineTo(x,      y + hh);
      floorGfx.lineTo(x - hw, y);
      floorGfx.closePath();
      floorGfx.endFill();

      // grid lines
      floorGfx.lineStyle(1, isRed ? CARPET_B : TILE_DARK, 0.4);
      floorGfx.moveTo(x, y - hh);
      floorGfx.lineTo(x + hw, y);
      floorGfx.lineTo(x, y + hh);
      floorGfx.lineTo(x - hw, y);
      floorGfx.closePath();
      floorGfx.lineStyle(0);
    }
  }
}

// ── draw furniture ────────────────────────────────────────────────────────────
function drawFurni() {
  for (const f of FURNITURE) {
    const { x, y } = sc(f.tx, f.ty);
    drawFurniAt(furniGfx, x, y, f.kind);
  }
}

function drawFurniAt(g: PIXI.Graphics, x: number, y: number, kind: string) {
  const hw = TILE_W / 2 - 4;

  switch (kind) {
    case 'column': {
      const H2 = 64;
      const W2 = 10;
      // Capital
      g.beginFill(COLUMN_A);
      g.drawRect(x - hw, y - H2 - 4, hw * 2, 6);
      g.endFill();
      // Shaft
      g.beginFill(COLUMN_B);
      g.drawRect(x - W2 / 2, y - H2 + 2, W2, H2);
      g.endFill();
      // Base
      g.beginFill(COLUMN_A);
      g.drawRect(x - 10, y - 4, 20, 6);
      g.endFill();
      // Gold trim
      g.lineStyle(1, GOLD, 0.8);
      g.drawRect(x - hw, y - H2 - 4, hw * 2, 6);
      g.lineStyle(0);
      break;
    }

    case 'throne': {
      // Seat
      g.beginFill(CARPET_A);
      g.drawRect(x - 16, y - 14, 32, 16);
      g.endFill();
      // Back
      g.beginFill(0x5c0e0e);
      g.drawRect(x - 16, y - 46, 32, 32);
      g.endFill();
      // Gold trim on back
      g.lineStyle(2, GOLD, 1);
      g.drawRect(x - 16, y - 46, 32, 32);
      g.lineStyle(0);
      // Crown-ish top ornament
      g.beginFill(GOLD);
      g.drawRect(x - 2, y - 52, 4, 8);
      g.drawRect(x - 8, y - 50, 4, 6);
      g.drawRect(x + 4,  y - 50, 4, 6);
      g.endFill();
      break;
    }

    case 'podium': {
      g.beginFill(WALL_A);
      g.drawRect(x - 14, y - 20, 28, 20);
      g.endFill();
      g.lineStyle(1, GOLD, 0.9);
      g.drawRect(x - 14, y - 20, 28, 20);
      g.lineStyle(0);
      // Acorn emblem
      g.beginFill(GOLD);
      g.drawCircle(x, y - 12, 4);
      g.drawRect(x - 2, y - 8, 4, 5);
      g.endFill();
      break;
    }

    case 'banner': {
      // Flag pole
      g.beginFill(0x808080);
      g.drawRect(x - 1, y - 60, 2, 60);
      g.endFill();
      // Flag
      g.beginFill(BANNER_R);
      g.drawRect(x + 1, y - 58, 20, 5);
      g.drawRect(x + 1, y - 48, 20, 5);
      g.drawRect(x + 1, y - 38, 20, 5);
      g.endFill();
      g.beginFill(BANNER_B);
      g.drawRect(x + 1, y - 58, 8, 13);
      g.endFill();
      // Stars dots
      g.beginFill(0xffffff);
      g.drawCircle(x + 3,  y - 55, 1);
      g.drawCircle(x + 6,  y - 55, 1);
      g.drawCircle(x + 3,  y - 52, 1);
      g.drawCircle(x + 6,  y - 52, 1);
      g.endFill();
      break;
    }

    case 'chair': {
      g.beginFill(0x6c3c20);
      g.drawRect(x - 10, y - 12, 20, 12);
      g.endFill();
      g.beginFill(0x4a2810);
      g.drawRect(x - 10, y - 28, 20, 16);
      g.endFill();
      g.lineStyle(1, GOLD, 0.5);
      g.drawRect(x - 10, y - 28, 20, 28);
      g.lineStyle(0);
      break;
    }
  }
}

// ── draw avatars ──────────────────────────────────────────────────────────────
function drawAvatars() {
  for (const av of AVATARS) {
    const { x, y } = sc(av.tx, av.ty);
    drawAvatar(x, y, av);
  }
}

function drawAvatar(x: number, y: number, av: Avatar) {
  const c = new PIXI.Container();

  const g = new PIXI.Graphics();

  if (av.isBot) {
    // Bot: grey robot style
    g.beginFill(0x607080);
    g.drawRoundedRect(-9, -38, 18, 16, 3);  // head
    g.endFill();
    g.beginFill(0x50606e);
    g.drawRect(-10, -22, 20, 20);            // body
    g.endFill();
    g.beginFill(0x8090a0);
    g.drawRect(-4, -36, 3, 3);              // eyes
    g.drawRect(2,  -36, 3, 3);
    g.endFill();
  } else {
    // tupci — admin: purple-themed
    // Hair
    g.beginFill(0x2a1a5c);
    g.drawRect(-10, -50, 20, 8);
    g.endFill();
    // Head
    g.beginFill(0xe0a070);
    g.drawRoundedRect(-9, -44, 18, 18, 4);
    g.endFill();
    // Eyes
    g.beginFill(0x1a0a3c);
    g.drawCircle(-3, -36, 2);
    g.drawCircle(4,  -36, 2);
    g.endFill();
    // Body — dark admin jacket
    g.beginFill(0x1e1040);
    g.drawRect(-11, -26, 22, 24);
    g.endFill();
    // Badge (gold star)
    g.beginFill(GOLD);
    g.drawStar(4, -18, 5, 4, 2);
    g.endFill();
    // Legs
    g.beginFill(0x2a2040);
    g.drawRect(-10, -2, 9, 8);
    g.drawRect(2,   -2, 9, 8);
    g.endFill();
    // Shoes
    g.beginFill(0x1a1020);
    g.drawRect(-10, 6, 9, 4);
    g.drawRect(2,   6, 9, 4);
    g.endFill();
  }

  c.addChild(g);

  // Name label
  const label = new PIXI.Text(av.isBot ? `[BOT] ${av.username}` : av.username, {
    fontSize: 10,
    fill: av.isBot ? 0x80aac0 : 0xc8a8ff,
    fontFamily: 'monospace',
    fontWeight: av.isBot ? 'normal' : 'bold',
  });
  label.anchor.set(0.5, 1);
  label.y = -54;
  c.addChild(label);

  // Rank star for admin
  if (!av.isBot) {
    const rank = new PIXI.Text('★★★★★★★', {
      fontSize: 8,
      fill: 0xc8a832,
      fontFamily: 'monospace',
    });
    rank.anchor.set(0.5, 1);
    rank.y = -44;
    c.addChild(rank);
  }

  c.x = x;
  c.y = y;
  avatarL.addChild(c);
}

// ── speech bubble for tupci ───────────────────────────────────────────────────
function drawSpeechBubble() {
  const av = AVATARS[0]; // tupci
  const { x, y } = sc(av.tx, av.ty);

  const bubble = new PIXI.Container();
  const bg = new PIXI.Graphics();

  const text = new PIXI.Text('White House room — test OK!', {
    fontSize: 11,
    fill: 0x1a1040,
    fontFamily: 'monospace',
  });

  const pad = 6;
  const bw = text.width + pad * 2;
  const bh = text.height + pad * 2;

  bg.beginFill(0xffffff);
  bg.lineStyle(1, 0xb090ff);
  bg.drawRoundedRect(0, 0, bw, bh, 5);
  bg.endFill();
  // Tail
  bg.beginFill(0xffffff);
  bg.lineStyle(0);
  bg.moveTo(12, bh);
  bg.lineTo(8,  bh + 8);
  bg.lineTo(20, bh);
  bg.closePath();
  bg.endFill();

  text.x = pad;
  text.y = pad;
  bubble.addChild(bg);
  bubble.addChild(text);
  bubble.x = x - bw / 2;
  bubble.y = y - 80 - bh;

  stage.addChild(bubble);
}

// ── compose ───────────────────────────────────────────────────────────────────
drawWalls();
drawFloor();
drawFurni();
drawAvatars();
drawSpeechBubble();

// Depth-sort avatar layer by y
avatarL.children.sort((a, b) => a.y - b.y);
