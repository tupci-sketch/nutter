import { z } from 'zod';

export const RoomModelSchema = z.object({
  id: z.string(),
  mapData: z.string(),
  doorX: z.number().int(),
  doorY: z.number().int(),
  doorDirection: z.number().int().min(0).max(7),
  poolMap: z.string().nullable(),
});

export type RoomModel = z.infer<typeof RoomModelSchema>;

export const RoomSchema = z.object({
  id: z.number().int().positive(),
  name: z.string().min(1).max(60),
  description: z.string().max(512),
  ownerId: z.number().int().positive(),
  ownerName: z.string(),
  modelId: z.string(),
  categoryId: z.number().int(),
  worldId: z.enum(['classic', 'nutropolis']),
  accessMode: z.number().int().min(0).max(2),
  password: z.string().nullable(),
  tradeMode: z.number().int().min(0).max(2),
  maxUsers: z.number().int().min(1).max(75),
  rating: z.number().int().min(0),
  tags: z.array(z.string()),
  allowPets: z.boolean(),
  allowPetsEat: z.boolean(),
  blockRoomWalk: z.boolean(),
  allowWalkthrough: z.boolean(),
  thumbnailUrl: z.string().nullable(),
  createdAt: z.string().datetime(),
  groupId: z.number().int().positive().nullable(),
  isPublic: z.boolean(),
  userCount: z.number().int().min(0),
  visitCount: z.number().int().min(0),
});

export type Room = z.infer<typeof RoomSchema>;

export type TileState = 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9;

export interface NavigationGrid {
  width: number;
  height: number;
  tiles: TileState[][];
  doorX: number;
  doorY: number;
  doorDirection: number;
}

export interface Position {
  x: number;
  y: number;
  z: number;
  direction: number;
}

export type AccessMode = 0 | 1 | 2;
export type TradeMode = 0 | 1 | 2;

export const AccessModeLabels: Record<AccessMode, string> = {
  0: 'Open',
  1: 'Doorbell',
  2: 'Password',
};

export const TradeModeLabels: Record<TradeMode, string> = {
  0: 'No trading',
  1: 'Owner only',
  2: 'Free trade',
};
