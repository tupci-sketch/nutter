import { describe, it, expect, beforeEach } from 'vitest';
import { FigureData, parseFigureString, buildFigureString, drawOrderOf } from '../FigureData';
import { AvatarComposer, mirrorDirection, spriteName, ACTION } from '../AvatarComposer';

/**
 * A small but realistic pair of data files: two colourable slots drawing from
 * different palettes, one non-colourable slot, and a set whose parts span two
 * draw layers.
 */
const FIGURE_DATA = `<?xml version="1.0"?>
<figuredata>
  <colors>
    <palette id="1">
      <color id="1" club="0" selectable="1">FFCB98</color>
      <color id="2" club="0" selectable="1">F5DA88</color>
    </palette>
    <palette id="2">
      <color id="61" club="0" selectable="1">2D2D2D</color>
      <color id="66" club="1" selectable="1">C99263</color>
    </palette>
  </colors>
  <sets>
    <settype type="hd" paletteid="1">
      <set id="180" gender="M" club="0" colorable="1" selectable="1">
        <part id="180" type="hd" colorable="1" index="0" colorindex="1"/>
      </set>
    </settype>
    <settype type="hr" paletteid="2">
      <set id="828" gender="M" club="0" colorable="1" selectable="1">
        <part id="828" type="hr" colorable="1" index="0" colorindex="1"/>
        <part id="828" type="hrb" colorable="1" index="1" colorindex="1"/>
      </set>
    </settype>
    <settype type="ch" paletteid="2">
      <set id="255" gender="M" club="0" colorable="0" selectable="1">
        <part id="255" type="ch" colorable="0" index="0" colorindex="1"/>
      </set>
    </settype>
  </sets>
</figuredata>`;

const FIGURE_MAP = `<?xml version="1.0"?>
<map>
  <lib id="hh_human_body" revision="1">
    <part id="180" type="hd"/>
  </lib>
  <lib id="hh_human_hair" revision="1">
    <part id="828" type="hr"/>
    <part id="828" type="hrb"/>
  </lib>
  <lib id="hh_human_shirt" revision="1">
    <part id="255" type="ch"/>
  </lib>
</map>`;

describe('figure string parsing', () => {
  it('splits a figure into typed selections with their colours', () => {
    const parts = parseFigureString('hd-180-1.hr-828-61.ch-255-66');

    expect(parts).toEqual([
      { type: 'hd', setId: '180', colours: ['1'] },
      { type: 'hr', setId: '828', colours: ['61'] },
      { type: 'ch', setId: '255', colours: ['66'] },
    ]);
  });

  it('keeps multiple colours on one part', () => {
    expect(parseFigureString('ch-255-66-1')[0].colours).toEqual(['66', '1']);
  });

  it('handles a part with no colour at all', () => {
    expect(parseFigureString('ch-255')[0].colours).toEqual([]);
  });

  it('drops malformed segments rather than throwing', () => {
    // A figure string arrives from the database and from other players; one bad
    // segment should cost a hat, not the whole avatar. A segment naming no set
    // ("bogus", "-") cannot resolve to a part, so it is dropped.
    const parts = parseFigureString('hd-180-1..bogus.-.ch-255-66');

    expect(parts.map((p) => p.type)).toEqual(['hd', 'ch']);
  });

  it('ignores a repeated slot so a part cannot be drawn twice', () => {
    const parts = parseFigureString('hd-180-1.hd-999-2');

    expect(parts).toHaveLength(1);
    expect(parts[0].setId).toBe('180');
  });

  it('returns nothing for an empty figure', () => {
    expect(parseFigureString('')).toEqual([]);
  });

  it('round-trips back to a figure string', () => {
    const figure = 'hd-180-1.hr-828-61-2';
    expect(buildFigureString(parseFigureString(figure))).toBe(figure);
  });
});

describe('direction mirroring', () => {
  it('leaves the stored directions alone', () => {
    for (const d of [0, 1, 2, 3, 7]) {
      expect(mirrorDirection(d)).toEqual({ direction: d, flip: false });
    }
  });

  it('mirrors 4, 5 and 6 from 2, 1 and 0', () => {
    // These three are not in the pack; composing them any other way leaves a
    // quarter of the compass with no avatar at all.
    expect(mirrorDirection(4)).toEqual({ direction: 2, flip: true });
    expect(mirrorDirection(5)).toEqual({ direction: 1, flip: true });
    expect(mirrorDirection(6)).toEqual({ direction: 0, flip: true });
  });

  it('wraps directions outside 0-7', () => {
    expect(mirrorDirection(8)).toEqual({ direction: 0, flip: false });
    expect(mirrorDirection(-1)).toEqual({ direction: 7, flip: false });
  });
});

describe('sprite naming', () => {
  it('builds the name the pack actually uses', () => {
    expect(
      spriteName({
        library: 'hh_human_hair',
        size: 'h',
        action: 'std',
        type: 'hr',
        partId: '828',
        direction: 2,
        frame: 0,
      }),
    ).toBe('hh_human_hair_h_std_hr_828_2_0');
  });
});

describe('draw order', () => {
  it('puts the body behind the head and the head behind hair', () => {
    expect(drawOrderOf('bd')).toBeLessThan(drawOrderOf('hd'));
    expect(drawOrderOf('hd')).toBeLessThan(drawOrderOf('hr'));
  });

  it('puts hair behind a hat', () => {
    expect(drawOrderOf('hr')).toBeLessThan(drawOrderOf('ha'));
  });

  it('puts a held item in front of everything else', () => {
    expect(drawOrderOf('ri')).toBeGreaterThan(drawOrderOf('ha'));
  });

  it('sorts an unknown type to the front rather than dropping it', () => {
    expect(drawOrderOf('zz')).toBeGreaterThanOrEqual(drawOrderOf('ri'));
  });
});

describe('AvatarComposer', () => {
  let data: FigureData;
  let composer: AvatarComposer;

  beforeEach(() => {
    data = new FigureData();
    data.parseFigureData(FIGURE_DATA);
    data.parseFigureMap(FIGURE_MAP);
    composer = new AvatarComposer(data);
  });

  it('parses the data files', () => {
    expect(data.loaded).toBe(true);
    expect(data.setTypeNames().sort()).toEqual(['ch', 'hd', 'hr']);
    expect(data.libraryFor('hr', '828')).toBe('hh_human_hair');
  });

  it('composes one layer per part, named for its library', () => {
    const layers = composer.compose({ figure: 'hd-180-1', direction: 2 });

    expect(layers).toHaveLength(1);
    expect(layers[0].sprite).toBe('hh_human_body_h_std_hd_180_2_0');
  });

  it('expands a set into all of its parts', () => {
    // Hairstyle 828 draws both the hair and the strands below a hat.
    const layers = composer.compose({ figure: 'hr-828-61', direction: 0 });

    expect(layers.map((l) => l.type).sort()).toEqual(['hr', 'hrb']);
  });

  it('orders layers back to front', () => {
    const layers = composer.compose({ figure: 'hd-180-1.ch-255-66.hr-828-61', direction: 0 });
    const order = layers.map((l) => l.type);

    expect(order.indexOf('ch')).toBeLessThan(order.indexOf('hd'));
    expect(order.indexOf('hd')).toBeLessThan(order.indexOf('hr'));
  });

  it('tints a colourable part from its palette', () => {
    const [hair] = composer.compose({ figure: 'hr-828-61', direction: 0 });

    // Hair draws from palette 2, where colour 61 is 2D2D2D.
    expect(hair.tint).toBe(0x2d2d2d);
  });

  it('resolves the same colour id differently per palette', () => {
    // Colour 1 means FFCB98 in the head palette. If palettes were shared, a
    // head would take whatever colour 1 happens to be elsewhere.
    const [head] = composer.compose({ figure: 'hd-180-1', direction: 0 });

    expect(head.tint).toBe(0xffcb98);
  });

  it('leaves a non-colourable part untinted', () => {
    // Shirt 255 is not colourable: tinting it would wash out the artwork.
    const [shirt] = composer.compose({ figure: 'ch-255-66', direction: 0 });

    expect(shirt.tint).toBeNull();
  });

  it('leaves a colourable part untinted when the figure names no colour', () => {
    const [hair] = composer.compose({ figure: 'hr-828', direction: 0 });

    expect(hair.tint).toBeNull();
  });

  it('picks the colour slot named by the part', () => {
    // Both hair parts use colour index 1, so both take the first colour.
    const layers = composer.compose({ figure: 'hr-828-61-1', direction: 0 });

    expect(layers.every((l) => l.tint === 0x2d2d2d)).toBe(true);
  });

  it('marks mirrored directions and names the stored one', () => {
    const [head] = composer.compose({ figure: 'hd-180-1', direction: 5 });

    expect(head.sprite).toContain('_1_0');
    expect(head.flip).toBe(true);
  });

  it('lets the head face a different way from the body', () => {
    const layers = composer.compose({
      figure: 'hd-180-1.ch-255-66',
      direction: 2,
      headDirection: 4,
    });

    const head = layers.find((l) => l.type === 'hd')!;
    const shirt = layers.find((l) => l.type === 'ch')!;

    expect(head.sprite).toContain('_2_0');
    expect(head.flip).toBe(true);
    expect(shirt.sprite).toContain('_2_0');
    expect(shirt.flip).toBe(false);
  });

  it('carries the action into the sprite name', () => {
    const [shirt] = composer.compose({
      figure: 'ch-255-66',
      direction: 0,
      action: ACTION.Walk,
      frame: 3,
    });

    expect(shirt.sprite).toBe('hh_human_shirt_h_wlk_ch_255_0_3');
  });

  it('keeps the head standing during a body-only action', () => {
    // A pack ships no head sprite for a wave, so a waving avatar would lose
    // its head if the action were applied to every part.
    const layers = composer.compose({
      figure: 'hd-180-1.ch-255-66',
      direction: 0,
      action: ACTION.Wave,
    });

    expect(layers.find((l) => l.type === 'hd')!.sprite).toContain('_std_');
    expect(layers.find((l) => l.type === 'ch')!.sprite).toContain('_wav_');
  });

  it('applies a full-body action to the head too', () => {
    const layers = composer.compose({
      figure: 'hd-180-1.ch-255-66',
      direction: 0,
      action: ACTION.Sit,
    });

    expect(layers.every((l) => l.sprite.includes('_sit_'))).toBe(true);
  });

  it('uses the requested sprite size', () => {
    const [head] = composer.compose({ figure: 'hd-180-1', direction: 0, size: 'sh' });

    expect(head.sprite).toBe('hh_human_body_sh_std_hd_180_0_0');
  });

  it('omits hidden part types', () => {
    const layers = composer.compose({
      figure: 'hd-180-1.hr-828-61',
      direction: 0,
      hide: ['hr'],
    });

    // Hiding the slot removes every part it contributes, including hrb.
    expect(layers.map((l) => l.type)).toEqual(['hd']);
  });

  it('skips a part the pack has no library for', () => {
    const layers = composer.compose({ figure: 'hd-180-1.zz-999-1', direction: 0 });

    expect(layers).toHaveLength(1);
    expect(layers[0].type).toBe('hd');
  });

  it('skips a set that figuredata does not define', () => {
    const layers = composer.compose({ figure: 'hd-999-1', direction: 0 });

    expect(layers).toEqual([]);
  });

  it('returns nothing for an empty figure rather than throwing', () => {
    expect(composer.compose({ figure: '', direction: 0 })).toEqual([]);
  });
});
