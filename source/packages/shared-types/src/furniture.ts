import { z } from 'zod';

export const FurnitureTypeSchema = z.enum(['floor', 'wall']);

export const PlacedFurniSchema = z.object({
  id: z.number().int().positive(),
  baseItemId: z.number().int().positive(),
  roomId: z.number().int().positive(),
  userId: z.number().int().positive(),
  type: FurnitureTypeSchema,
  x: z.number().int(),
  y: z.number().int(),
  z: z.number(),
  rotation: z.number().int().min(0).max(7),
  wallX: z.number().int().nullable(),
  wallY: z.number().int().nullable(),
  wallLocation: z.enum(['l', 'r', 's']).nullable(),
  state: z.string(),
  extra: z.string().nullable(),
  groupId: z.number().int().positive().nullable(),
});

export type PlacedFurni = z.infer<typeof PlacedFurniSchema>;

export const InventoryFurniSchema = z.object({
  id: z.number().int().positive(),
  baseItemId: z.number().int().positive(),
  userId: z.number().int().positive(),
  type: FurnitureTypeSchema,
  extra: z.string().nullable(),
  limitedEditionNumber: z.number().int().positive().nullable(),
  limitedEditionTotal: z.number().int().positive().nullable(),
  acquiredAt: z.string().datetime(),
});

export type InventoryFurni = z.infer<typeof InventoryFurniSchema>;

export interface TeleportLink {
  furniIdA: number;
  furniIdB: number;
  roomIdA: number;
  roomIdB: number;
}

export const ROTATION_DIRECTIONS = [0, 2, 4, 6] as const;
export type RotationDirection = (typeof ROTATION_DIRECTIONS)[number];

export const MAX_STACK_HEIGHT = 10;
export const STACK_HEIGHT_INCREMENT = 0.001;

export function nextRotation(current: RotationDirection): RotationDirection {
  const idx = ROTATION_DIRECTIONS.indexOf(current);
  return ROTATION_DIRECTIONS[(idx + 1) % ROTATION_DIRECTIONS.length] ?? 0;
}
