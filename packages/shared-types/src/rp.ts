import { z } from 'zod';

export const RpFactionTagSchema = z.enum([
  'police', 'fire', 'medical', 'government', 'criminal', 'civilian', 'business', 'media',
]);

export type RpFactionTag = z.infer<typeof RpFactionTagSchema>;

export const RpPropertyTypeSchema = z.enum(['residence', 'business', 'government', 'land']);
export type RpPropertyType = z.infer<typeof RpPropertyTypeSchema>;

export const RpCharacterSchema = z.object({
  id: z.number().int().positive(),
  userId: z.number().int().positive(),
  name: z.string().min(2).max(30).regex(/^[a-zA-Z\s\-']+$/),
  surname: z.string().min(2).max(30).regex(/^[a-zA-Z\s\-']+$/),
  age: z.number().int().min(18).max(99),
  biography: z.string().max(512),
  factionId: z.number().int().positive().nullable(),
  factionTag: RpFactionTagSchema.nullable(),
  jobId: z.number().int().positive().nullable(),
  jobTitle: z.string().nullable(),
  cashBalance: z.number().int().min(0),
  bankBalance: z.number().int().min(0),
  health: z.number().int().min(0).max(100),
  prisonExpiry: z.string().datetime().nullable(),
  createdAt: z.string().datetime(),
});

export type RpCharacter = z.infer<typeof RpCharacterSchema>;

export const RpFactionSchema = z.object({
  id: z.number().int().positive(),
  name: z.string(),
  tag: RpFactionTagSchema,
  description: z.string(),
  memberCount: z.number().int().min(0),
  maxMembers: z.number().int().positive(),
  isRecruiting: z.boolean(),
  badgeId: z.string(),
  leaderId: z.number().int().positive().nullable(),
  leaderName: z.string().nullable(),
  hqRoomId: z.number().int().positive().nullable(),
});

export type RpFaction = z.infer<typeof RpFactionSchema>;

export const GOVERNMENT_OFFICE_TERMS = {
  mayor: 30 * 24 * 60 * 60 * 1000,
  councillor: 30 * 24 * 60 * 60 * 1000,
  judge: 90 * 24 * 60 * 60 * 1000,
} as const;

export const RpCrimeTypes = [
  'theft',
  'assault',
  'murder',
  'drug_possession',
  'drug_trafficking',
  'fraud',
  'vandalism',
  'trespassing',
  'illegal_weapons',
  'evading_arrest',
  'bribery',
  'money_laundering',
  'reckless_driving',
  'robbery',
  'kidnapping',
] as const;

export type RpCrimeType = (typeof RpCrimeTypes)[number];

export const RP_CURRENCY_IRON_WALL = true as const;
