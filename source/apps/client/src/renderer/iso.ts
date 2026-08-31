export const TILE_W = 64;
export const TILE_H = 32;

export function toScreen(tx: number, ty: number, tz: number = 0): { x: number; y: number } {
  return {
    x: (tx - ty) * (TILE_W / 2),
    y: (tx + ty) * (TILE_H / 2) - tz * TILE_H,
  };
}

export function toTile(sx: number, sy: number): { tx: number; ty: number } {
  const tx = (sx / (TILE_W / 2) + sy / (TILE_H / 2)) / 2;
  const ty = (sy / (TILE_H / 2) - sx / (TILE_W / 2)) / 2;
  return { tx: Math.floor(tx), ty: Math.floor(ty) };
}

export const FLOOR_COLOUR = 0x7b6c5c;
export const FLOOR_DARK   = 0x5e5147;
export const WALL_COLOUR  = 0x8c7d6e;
export const WALL_DARK    = 0x6e6055;
