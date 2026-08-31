export const RateLimits = {
  CHAT_PER_SECOND: 5,
  CHAT_BURST: 8,
  MOVEMENT_PER_SECOND: 10,
  FURNI_PER_SECOND: 20,
  CATALOGUE_PURCHASE_PER_SECOND: 5,
  COMBINED_PER_SECOND: 100,
} as const;

export type RateLimitKey = keyof typeof RateLimits;
