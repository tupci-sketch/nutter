import { describe, it, expect } from 'vitest';
import { toScreen, toTile, TILE_W, TILE_H } from '../renderer/iso';

describe('isometric coordinate transforms', () => {
  it('converts tile origin to screen origin', () => {
    const { x, y } = toScreen(0, 0, 0);
    expect(x).toBe(0);
    expect(y).toBe(0);
  });

  it('advances x by half tile width per tile x step', () => {
    const { x } = toScreen(1, 0, 0);
    expect(x).toBe(TILE_W / 2);
  });

  it('advances y by half tile height per tile y step', () => {
    const { y } = toScreen(0, 1, 0);
    expect(y).toBe(TILE_H / 2);
  });

  it('toTile round-trips toScreen at integer coords', () => {
    const { x, y } = toScreen(3, 5, 0);
    const tile = toTile(x, y);
    expect(tile.tx).toBe(3);
    expect(tile.ty).toBe(5);
  });

  it('z offset raises the y position', () => {
    const ground = toScreen(0, 0, 0);
    const raised  = toScreen(0, 0, 1);
    expect(raised.y).toBeLessThan(ground.y);
  });
});
