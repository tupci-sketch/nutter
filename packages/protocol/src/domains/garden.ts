export const GardenPacketTypes = {
  // c2s
  STATE: 'garden.state',
  PLOT_CLAIM: 'garden.plot.claim',
  PLOT_RELEASE: 'garden.plot.release',
  PLANT: 'garden.plant',
  WATER: 'garden.water',
  HARVEST: 'garden.harvest',
  INSPECT: 'garden.inspect',
  GOAL_STATUS: 'garden.goal.status',

  // s2c
  STATE_RESULT: 'garden.state.result',
  PLOT_CLAIMED: 'garden.plot.claimed',
  PLOT_RELEASED: 'garden.plot.released',
  PLANTED: 'garden.planted',
  WATERED: 'garden.watered',
  HARVESTED: 'garden.harvested',
  WITHERED: 'garden.withered',
  GROWN: 'garden.grown',
  INSPECT_RESULT: 'garden.inspect.result',
  GOAL_UPDATED: 'garden.goal.updated',
  GOAL_COMPLETED: 'garden.goal.completed',
  SEASON_CHANGED: 'garden.season.changed',
  ERROR: 'garden.error',
} as const;

export type GardenPacketType = (typeof GardenPacketTypes)[keyof typeof GardenPacketTypes];

export type PlantType = 'sunflower' | 'rosebush' | 'oak_sapling' | 'pumpkin' | 'strawberry' | 'mushroom' | 'cactus' | 'bluebell' | 'snapdragon' | 'ghost_plant';
export type PlantStage = 'seed' | 'sprout' | 'growing' | 'mature' | 'ready' | 'withered';
export type GardenSeason = 'spring' | 'summer' | 'autumn' | 'winter';

export interface GardenPlot {
  plotId: number;
  ownerId: number | null;
  ownerName: string | null;
  plantType: PlantType | null;
  stage: PlantStage | null;
  wateredAt: string | null;
  plantedAt: string | null;
  readyAt: string | null;
  witheredAt: string | null;
  harvestYield: number;
}

export interface GardenGoal {
  id: string;
  description: string;
  targetCount: number;
  currentCount: number;
  plantType: PlantType | null;
  rewardDescription: string;
  completedAt: string | null;
  expiresAt: string;
}

export interface GardenStateResultPayload {
  season: GardenSeason;
  seasonEndsAt: string;
  plots: GardenPlot[];
  myPlotId: number | null;
  activeGoal: GardenGoal | null;
  availablePlants: Array<{
    type: PlantType;
    growthTimeMs: number;
    wateringIntervalMs: number;
    harvestYield: number;
    seasonalBonus: boolean;
  }>;
}

export interface GardenPlantPayload {
  plotId: number;
  plantType: PlantType;
}

export interface GardenWaterPayload {
  plotId: number;
}

export interface GardenHarvestPayload {
  plotId: number;
}

export interface GardenHarvestedPayload {
  plotId: number;
  plantType: PlantType;
  yield: number;
  nutPointsGranted: number;
  contributedToGoal: boolean;
  goalProgress: number;
}

export interface GardenGoalCompletedPayload {
  goalId: string;
  rewardDescription: string;
  participants: Array<{ userId: number; username: string; contribution: number }>;
}
