export const QuestPacketTypes = {
  // c2s
  LIST: 'quest.list',
  ACCEPT: 'quest.accept',
  ABANDON: 'quest.abandon',
  ACTIVE: 'quest.active',

  // s2c
  LIST_RESULT: 'quest.list.result',
  ACCEPTED: 'quest.accepted',
  ABANDONED: 'quest.abandoned',
  ACTIVE_RESULT: 'quest.active.result',
  PROGRESS_UPDATED: 'quest.progress.updated',
  COMPLETED: 'quest.completed',
  EXPIRED: 'quest.expired',
  ROTATION: 'quest.rotation',
} as const;

export type QuestPacketType = (typeof QuestPacketTypes)[keyof typeof QuestPacketTypes];

export type QuestType = 'daily' | 'weekly' | 'seasonal' | 'story' | 'special';
export type QuestObjectiveType = 'chat_messages' | 'rooms_visited' | 'furni_placed' | 'games_played' | 'games_won' | 'friends_added' | 'items_purchased' | 'items_traded' | 'achievements_unlocked' | 'garden_harvested' | 'quests_completed';

export interface QuestObjective {
  type: QuestObjectiveType;
  required: number;
  current: number;
  description: string;
}

export interface QuestInfo {
  id: string;
  name: string;
  description: string;
  type: QuestType;
  objectives: QuestObjective[];
  rewardCredits: number;
  rewardDiamonds: number;
  rewardNutPoints: number;
  rewardBadgeId: string | null;
  expiresAt: string | null;
  acceptedAt: string | null;
  completedAt: string | null;
  available: boolean;
}

export interface QuestCompletedPayload {
  questId: string;
  questName: string;
  rewardCredits: number;
  rewardDiamonds: number;
  rewardNutPoints: number;
  rewardBadgeId: string | null;
  creditsBalance: number;
  diamondsBalance: number;
  nutPointsBalance: number;
}

export interface QuestRotationPayload {
  type: QuestType;
  newQuests: QuestInfo[];
  nextRotationAt: string;
}
