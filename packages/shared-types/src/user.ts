import { z } from 'zod';

export const UserRank = {
  PLAYER: 1,
  VIP: 2,
  HELPER: 3,
  MODERATOR: 4,
  SUPER_MODERATOR: 5,
  ADMIN: 6,
  OWNER: 7,
} as const;

export type UserRankValue = (typeof UserRank)[keyof typeof UserRank];

export const RankNames: Record<UserRankValue, string> = {
  1: 'Player',
  2: 'VIP',
  3: 'Helper',
  4: 'Moderator',
  5: 'Super Moderator',
  6: 'Administrator',
  7: 'Owner',
};

export const UserSchema = z.object({
  id: z.number().int().positive(),
  username: z.string().min(3).max(25).regex(/^[a-zA-Z0-9_\-=?!@:.,]+$/),
  figureString: z.string(),
  gender: z.enum(['M', 'F']),
  motto: z.string().max(190),
  rank: z.number().int().min(1).max(7),
  email: z.string().email(),
  emailVerified: z.boolean(),
  twoFaEnabled: z.boolean(),
  memberSince: z.string().datetime(),
  lastLogin: z.string().datetime().nullable(),
  credits: z.number().int().min(0),
  diamonds: z.number().int().min(0),
  nutPoints: z.number().int().min(0),
  seasonal: z.number().int().min(0),
  membershipTier: z.enum(['none', 'bronze', 'silver', 'gold', 'diamond']),
  membershipExpiry: z.string().datetime().nullable(),
  achievementScore: z.number().int().min(0),
});

export type User = z.infer<typeof UserSchema>;

export const SessionSchema = z.object({
  sessionId: z.string().uuid(),
  userId: z.number().int().positive(),
  ticket: z.string(),
  worldId: z.enum(['classic', 'nutropolis']),
  issuedAt: z.string().datetime(),
  expiresAt: z.string().datetime(),
  machineId: z.string().nullable(),
  ipAddress: z.string(),
});

export type Session = z.infer<typeof SessionSchema>;

export const BanSchema = z.object({
  id: z.number().int().positive(),
  userId: z.number().int().positive(),
  username: z.string(),
  bannedById: z.number().int().positive(),
  bannedByName: z.string(),
  reason: z.string(),
  banType: z.enum(['standard', 'ip', 'machine', 'account_family']),
  ipAddress: z.string().nullable(),
  machineId: z.string().nullable(),
  createdAt: z.string().datetime(),
  expiresAt: z.string().datetime().nullable(),
  isActive: z.boolean(),
  liftedAt: z.string().datetime().nullable(),
  liftedById: z.number().int().positive().nullable(),
});

export type Ban = z.infer<typeof BanSchema>;
