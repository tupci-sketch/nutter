export const FurniPacketTypes = {
  // c2s
  PLACE: 'furni.place',
  MOVE: 'furni.move',
  ROTATE: 'furni.rotate',
  PICKUP: 'furni.pickup',
  INTERACT: 'furni.interact',
  USE: 'furni.use',
  USE_WITH: 'furni.use_with',
  WALL_PLACE: 'furni.wall.place',
  WALL_MOVE: 'furni.wall.move',
  WALL_PICKUP: 'furni.wall.pickup',
  DICE_ROLL: 'furni.dice.roll',
  DICE_OFF: 'furni.dice.off',
  TELEPORT_LINK: 'furni.teleport.link',
  TELEPORT_USE: 'furni.teleport.use',

  // s2c
  PLACED: 'furni.placed',
  MOVED: 'furni.moved',
  ROTATED: 'furni.rotated',
  PICKED_UP: 'furni.picked_up',
  INTERACTED: 'furni.interacted',
  WALL_PLACED: 'furni.wall.placed',
  WALL_MOVED: 'furni.wall.moved',
  WALL_PICKED_UP: 'furni.wall.picked_up',
  ROLLER_MOVE: 'furni.roller.move',
  STATE_CHANGED: 'furni.state.changed',
  DICE_RESULT: 'furni.dice.result',
  TELEPORT_LINKED: 'furni.teleport.linked',
  PLACE_ERROR: 'furni.place.error',
} as const;

export type FurniPacketType = (typeof FurniPacketTypes)[keyof typeof FurniPacketTypes];

export interface FloorFurniData {
  id: number;
  baseItemId: number;
  x: number;
  y: number;
  z: number;
  rotation: number;
  ownerId: number;
  ownerName: string;
  state: string;
  extra: string | null;
}

export interface WallFurniData {
  id: number;
  baseItemId: number;
  wx: number;
  wy: number;
  wl: 'l' | 'r' | 's';
  sx: number;
  sy: number;
  ownerId: number;
  ownerName: string;
  state: string;
}

export interface FurniPlacePayload {
  itemId: number;
  x: number;
  y: number;
  rotation: number;
}

export interface FurniMovePayload {
  furniId: number;
  x: number;
  y: number;
  rotation: number;
}

export interface FurniRotatePayload {
  furniId: number;
  rotation: number;
}

export interface FurniPickupPayload {
  furniId: number;
}

export interface FurniInteractPayload {
  furniId: number;
  interactionParam?: number;
}

export interface FurniWallPlacePayload {
  itemId: number;
  wx: number;
  wy: number;
  wl: 'l' | 'r' | 's';
  sx: number;
  sy: number;
}

export interface FurniWallMovePayload {
  furniId: number;
  wx: number;
  wy: number;
  wl: 'l' | 'r' | 's';
  sx: number;
  sy: number;
}

export interface FurniRollerMovePayload {
  entities: Array<{
    type: 'user' | 'furni';
    id: number;
    fromX: number;
    fromY: number;
    fromZ: number;
    toX: number;
    toY: number;
    toZ: number;
  }>;
}

export interface FurniStateChangedPayload {
  furniId: number;
  state: string;
}

export interface FurniPlaceErrorPayload {
  itemId: number;
  reason: string;
}
