import { z } from 'zod';

export type Currency = 'credits' | 'diamonds' | 'nut_points' | 'seasonal' | 'rp_cash';

export const CurrencyLabels: Record<Currency, string> = {
  credits: 'Credits',
  diamonds: 'Diamonds',
  nut_points: 'Nut Points',
  seasonal: 'Seasonal Tokens',
  rp_cash: 'RP Cash',
};

export const CurrencyIcons: Record<Currency, string> = {
  credits: 'icon_credits',
  diamonds: 'icon_diamonds',
  nut_points: 'icon_nut_points',
  seasonal: 'icon_seasonal',
  rp_cash: 'icon_rp_cash',
};

export const TransactionSchema = z.object({
  id: z.number().int().positive(),
  transactionHash: z.string().length(64),
  idempotencyKey: z.string().uuid(),
  userId: z.number().int().positive(),
  type: z.string(),
  currency: z.enum(['credits', 'diamonds', 'nut_points', 'seasonal', 'rp_cash']),
  amount: z.number().int(),
  balanceBefore: z.number().int().min(0),
  balanceAfter: z.number().int().min(0),
  description: z.string(),
  referenceId: z.string().nullable(),
  referenceType: z.string().nullable(),
  performedById: z.number().int().positive().nullable(),
  createdAt: z.string().datetime(),
});

export type Transaction = z.infer<typeof TransactionSchema>;

export const CatalogueItemSchema = z.object({
  id: z.number().int().positive(),
  baseItemId: z.number().int().positive(),
  name: z.string(),
  description: z.string(),
  priceCredits: z.number().int().min(0),
  priceDiamonds: z.number().int().min(0),
  priceNutPoints: z.number().int().min(0),
  priceSeasonal: z.number().int().min(0),
  amount: z.number().int().min(1),
  isLimited: z.boolean(),
  limitedTotal: z.number().int().positive().nullable(),
  limitedSold: z.number().int().min(0).nullable(),
  giftable: z.boolean(),
  clubOnly: z.boolean(),
  enabled: z.boolean(),
  pageId: z.number().int().positive(),
  order: z.number().int().min(0),
});

export type CatalogueItem = z.infer<typeof CatalogueItemSchema>;

export const ItemBaseSchema = z.object({
  id: z.number().int().positive(),
  spriteId: z.string(),
  name: z.string(),
  description: z.string(),
  type: z.enum(['floor', 'wall', 'clothing', 'effect', 'badge', 'pet']),
  width: z.number().int().min(1),
  height: z.number().int().min(1),
  stackHeight: z.number(),
  canStack: z.boolean(),
  canSit: z.boolean(),
  isWalkable: z.boolean(),
  isTradeable: z.boolean(),
  isRecyclable: z.boolean(),
  isGroupable: z.boolean(),
  interactionType: z.string(),
  interactionModes: z.number().int().min(0),
  furniLineId: z.string().nullable(),
  environment: z.string().nullable(),
});

export type ItemBase = z.infer<typeof ItemBaseSchema>;

export const MembershipTierLimits = {
  none: { maxRooms: 25, maxFurni: 2000, tradeDiscount: 0 },
  bronze: { maxRooms: 50, maxFurni: 5000, tradeDiscount: 0.05 },
  silver: { maxRooms: 100, maxFurni: 10000, tradeDiscount: 0.10 },
  gold: { maxRooms: 200, maxFurni: 25000, tradeDiscount: 0.15 },
  diamond: { maxRooms: 500, maxFurni: 50000, tradeDiscount: 0.20 },
} as const;

export type MembershipTier = keyof typeof MembershipTierLimits;
