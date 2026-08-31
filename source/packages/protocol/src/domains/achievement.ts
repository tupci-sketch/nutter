export const AchievementPacketTypes = {
  // c2s
  LIST: 'achievement.list',
  INFO: 'achievement.info',
  PROGRESS: 'achievement.progress',

  // s2c
  LIST_RESULT: 'achievement.list.result',
  INFO_RESULT: 'achievement.info.result',
  PROGRESS_RESULT: 'achievement.progress.result',
  UNLOCKED: 'achievement.unlocked',
  SCORE_UPDATED: 'achievement.score.updated',
} as const;

export type AchievementPacketType = (typeof AchievementPacketTypes)[keyof typeof AchievementPacketTypes];

export type AchievementCategory = 'social' | 'rooms' | 'games' | 'trading' | 'pets' | 'bots' | 'wired' | 'roleplay' | 'garden' | 'general' | 'seasonal';

export interface AchievementLevel {
  level: number;
  progressRequired: number;
  badgeId: string;
  rewardCredits: number;
  rewardDiamonds: number;
  rewardNutPoints: number;
}

export interface AchievementInfo {
  id: string;
  name: string;
  description: string;
  category: AchievementCategory;
  levels: AchievementLevel[];
  currentLevel: number;
  currentProgress: number;
  completedAt: string | null;
}

export interface AchievementUnlockedPayload {
  achievementId: string;
  achievementName: string;
  level: number;
  badgeId: string;
  rewardCredits: number;
  rewardDiamonds: number;
  rewardNutPoints: number;
  newScore: number;
}

export interface AchievementScoreUpdatedPayload {
  totalScore: number;
  rankTitle: string;
}
