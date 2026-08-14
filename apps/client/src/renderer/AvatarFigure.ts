import * as PIXI from 'pixi.js';
import { AssetLoader } from './AssetLoader';
import type { RoomUser } from '@/stores/roomStore';

// Standard Habbo figure part render order (back-to-front within a direction).
// Each entry is [partType, libPrefix] where libPrefix is the first segment of
// the SWF library name (e.g. "hh_human_body" for "bd" body parts).
const FIGURE_PARTS: Array<{ type: string; lib: string }> = [
  { type: 'bd', lib: 'hh_human_body'   }, // body
  { type: 'sh', lib: 'hh_human_shoes'  }, // shoes
  { type: 'lg', lib: 'hh_human_legs'   }, // legs
  { type: 'ch', lib: 'hh_human_shirt'  }, // shirt / chest
  { type: 'ca', lib: 'hh_human_coat'   }, // coat
  { type: 'wa', lib: 'hh_human_waist'  }, // waist accessory
  { type: 'hd', lib: 'hh_human_head'   }, // head (face)
  { type: 'fc', lib: 'hh_human_head'   }, // face (eyes/mouth)
  { type: 'ey', lib: 'hh_human_head'   }, // eyes
  { type: 'hr', lib: 'hh_human_hair'   }, // hair
  { type: 'hrb',lib: 'hh_human_hair'   }, // hair below hat
  { type: 'ha', lib: 'hh_human_hat'    }, // hat
  { type: 'ea', lib: 'hh_human_head'   }, // earring
  { type: 'fa', lib: 'hh_human_head'   }, // face accessory
  { type: 'cc', lib: 'hh_human_chest'  }, // chest accessory
  { type: 'ba', lib: 'hh_human_arm'    }, // arm
  { type: 'ri', lib: 'hh_human_right'  }, // right-hand item
  { type: 'li', lib: 'hh_human_left'   }, // left-hand item
];

// Habbo avatar directions use 0-7; the renderer maps dir→sprite dir here.
// Only even directions (0,2,4,6) have distinct sprites; odd ones mirror them.
function spriteDir(dir: number): number {
  return dir & ~1; // round down to nearest even
}

/**
 * AvatarFigure renders a single hotel user as layered figure sprites.
 *
 * When the figure sprite atlas is available from an installed SWF pack the
 * layers are loaded asynchronously and composited in the correct order.
 * Without assets it draws the same head+body rectangle as before, so rooms
 * always render even during setup.
 */
export class AvatarFigure extends PIXI.Container {
  private user: RoomUser;
  private partSprites: PIXI.Sprite[] = [];
  private frameTimer: ReturnType<typeof setInterval> | null = null;
  private frame = 0;

  constructor(user: RoomUser) {
    super();
    this.user = user;
    this.sortableChildren = false;
    this.rebuild();
  }

  // ─── public API ───────────────────────────────────────────────────────────

  update(user: RoomUser): void {
    const changed = user.dir !== this.user.dir || user.figure !== this.user.figure;
    this.user = user;
    if (changed) this.rebuild();
  }

  destroy(options?: PIXI.IDestroyOptions | boolean): void {
    this.stopAnimation();
    super.destroy(options);
  }

  // ─── rendering ────────────────────────────────────────────────────────────

  private rebuild(): void {
    this.stopAnimation();
    this.removeChildren();
    this.partSprites = [];

    if (AssetLoader.hasSprites()) {
      void this.buildFromSprites();
    } else {
      this.buildPlaceholder();
    }
  }

  private async buildFromSprites(): Promise<void> {
    const dir = spriteDir(this.user.dir ?? 2);
    const parsedParts = parseFigureString(this.user.figure ?? '');

    let loaded = 0;
    for (const partDef of FIGURE_PARTS) {
      const partInfo = parsedParts.find(p => p.type === partDef.type);
      if (!partInfo) continue;

      // Resolve which library owns this part type.
      const lib = AssetLoader.figureLibForPartType(partDef.type);
      const libId = lib?.id ?? partDef.lib;

      const tex = await AssetLoader.getAvatarPartTexture(libId, partDef.type, dir, this.frame);
      if (!tex) continue;

      const sprite = new PIXI.Sprite(tex);
      sprite.anchor.set(0.5, 1); // feet at origin
      this.addChild(sprite);
      this.partSprites.push(sprite);
      loaded++;
    }

    if (loaded === 0) {
      this.buildPlaceholder();
    } else {
      this.addUsernameLabel();
      this.startAnimation();
    }
  }

  private buildPlaceholder(): void {
    const c = new PIXI.Container();

    // Head
    const head = new PIXI.Graphics();
    head.beginFill(0xd4a76a);
    head.drawRoundedRect(-10, -40, 20, 20, 3);
    head.endFill();
    c.addChild(head);

    // Body
    const body = new PIXI.Graphics();
    body.beginFill(0x4a7fc1);
    body.drawRect(-10, -20, 20, 24);
    body.endFill();
    c.addChild(body);

    this.addChild(c);
    this.addUsernameLabel();
  }

  private addUsernameLabel(): void {
    const label = new PIXI.Text(this.user.username, {
      fontSize:   10,
      fill:       0xffffff,
      fontFamily: 'monospace',
      stroke:     0x000000,
      strokeThickness: 2,
    });
    label.anchor.set(0.5, 1);
    label.y = -46;
    this.addChild(label);
  }

  // ─── walk animation ───────────────────────────────────────────────────────

  private startAnimation(): void {
    // Idle animation ticks slowly; walk animation would be faster (driven by
    // movement events).  For now we use a gentle 300ms idle cycle.
    this.frameTimer = setInterval(() => void this.advanceFrame(), 300);
  }

  private stopAnimation(): void {
    if (this.frameTimer !== null) {
      clearInterval(this.frameTimer);
      this.frameTimer = null;
    }
  }

  private async advanceFrame(): Promise<void> {
    this.frame = (this.frame + 1) % 4; // most figure animations have 4 frames
    const dir = spriteDir(this.user.dir ?? 2);
    const parsedParts = parseFigureString(this.user.figure ?? '');
    let idx = 0;
    for (const partDef of FIGURE_PARTS) {
      if (!parsedParts.find(p => p.type === partDef.type)) continue;
      const lib = AssetLoader.figureLibForPartType(partDef.type);
      const libId = lib?.id ?? partDef.lib;
      const tex = await AssetLoader.getAvatarPartTexture(libId, partDef.type, dir, this.frame);
      if (tex && this.partSprites[idx]) {
        this.partSprites[idx].texture = tex;
      }
      idx++;
    }
  }
}

// ─── figure string parser ────────────────────────────────────────────────────

interface FigurePart {
  type: string;
  id: number;
  colors: number[];
}

/** Parse a figure string like "hd-180-2.bd-110-62.lg-280-110" */
function parseFigureString(figure: string): FigurePart[] {
  if (!figure) return defaultFigureParts();
  return figure.split('.').map(segment => {
    const [type, id, ...colors] = segment.split('-');
    return {
      type:   type ?? 'hd',
      id:     parseInt(id ?? '180'),
      colors: colors.map(Number),
    };
  });
}

function defaultFigureParts(): FigurePart[] {
  return [
    { type: 'hd', id: 180, colors: [2]  },
    { type: 'bd', id: 110, colors: [62] },
    { type: 'lg', id: 280, colors: [110]},
  ];
}
