import { describe, expect, it } from 'vitest';
import { artworkOf } from '../AssetLoader';

describe('artworkOf', () => {
  it('draws a colour variant from its base item', () => {
    expect(artworkOf('chair_norja*2')).toBe('chair_norja');
    expect(artworkOf('chair_norja')).toBe('chair_norja');
  });
});
