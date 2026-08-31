import { z } from 'zod';

export const AuditLogSchema = z.object({
  id: z.number().int().positive(),
  performedById: z.number().int().positive(),
  performedByName: z.string(),
  performedByIp: z.string(),
  action: z.string(),
  targetType: z.string().nullable(),
  targetId: z.string().nullable(),
  targetName: z.string().nullable(),
  beforeState: z.unknown().nullable(),
  afterState: z.unknown().nullable(),
  metadata: z.record(z.unknown()),
  sessionId: z.string().nullable(),
  roomId: z.number().int().positive().nullable(),
  worldId: z.enum(['classic', 'nutropolis', 'system']),
  createdAt: z.string().datetime(),
});

export type AuditLog = z.infer<typeof AuditLogSchema>;

export const AUDIT_LOG_RETENTION_DAYS = 365;

export const WordFilterSeverity = {
  LOW: 1,
  MEDIUM: 2,
  HIGH: 3,
} as const;

export type WordFilterSeverityValue = (typeof WordFilterSeverity)[keyof typeof WordFilterSeverity];

export const WordFilterAction = {
  BLOCK: 'block',
  REPLACE: 'replace',
  LOG: 'log',
} as const;

export const AppealSchema = z.object({
  id: z.number().int().positive(),
  userId: z.number().int().positive(),
  username: z.string(),
  banId: z.number().int().positive(),
  message: z.string().min(10).max(1000),
  status: z.enum(['pending', 'accepted', 'rejected']),
  reviewedById: z.number().int().positive().nullable(),
  reviewedByName: z.string().nullable(),
  reviewNote: z.string().nullable(),
  submittedAt: z.string().datetime(),
  reviewedAt: z.string().datetime().nullable(),
});

export type Appeal = z.infer<typeof AppealSchema>;
