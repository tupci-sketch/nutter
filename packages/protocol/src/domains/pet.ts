export const PetPacketTypes = {
  // c2s
  PLACE: 'pet.place',
  PICKUP: 'pet.pickup',
  COMMAND: 'pet.command',
  INFO: 'pet.info',
  BREED: 'pet.breed',
  RENAME: 'pet.rename',
  EQUIP: 'pet.equip',
  FEED: 'pet.feed',

  // s2c
  PLACED: 'pet.placed',
  PICKED_UP: 'pet.picked_up',
  MOVED: 'pet.moved',
  STATE_CHANGED: 'pet.state.changed',
  STAT_UPDATED: 'pet.stat.updated',
  LEVELED_UP: 'pet.leveled_up',
  COMMAND_PERFORMED: 'pet.command.performed',
  INFO_RESULT: 'pet.info.result',
  BRED: 'pet.bred',
  RENAMED: 'pet.renamed',
  EQUIPPED: 'pet.equipped',
  FED: 'pet.fed',
  ERROR: 'pet.error',
} as const;

export type PetPacketType = (typeof PetPacketTypes)[keyof typeof PetPacketTypes];

export type PetType = 'dog' | 'cat' | 'croco' | 'bear' | 'pig' | 'bunny' | 'terrier' | 'chicken' | 'frog' | 'dragon' | 'gnome' | 'monsterplant';
export type PetCommand = 'sit' | 'down' | 'shake' | 'jump' | 'speak' | 'heel' | 'beg' | 'play' | 'spin' | 'stay';

export interface PetStats {
  hunger: number;
  thirst: number;
  happiness: number;
  energy: number;
  hygiene: number;
  level: number;
  xp: number;
  xpToNext: number;
  trained: PetCommand[];
}

export interface PetInfo {
  id: number;
  type: PetType;
  name: string;
  ownerId: number;
  ownerName: string;
  stats: PetStats;
  genetics: {
    colorA: number;
    colorB: number;
    pattern: number;
    hairStyle: number;
    hairColor: number;
  };
  equipmentIds: number[];
  rarity: 'common' | 'uncommon' | 'rare' | 'legendary';
}

export interface PetPlacePayload {
  petId: number;
  x: number;
  y: number;
  direction: number;
}

export interface PetCommandPayload {
  petId: number;
  command: PetCommand;
}

export interface PetBreedPayload {
  petAId: number;
  petBId: number;
}

export interface PetBredPayload {
  newPet: PetInfo;
  petAId: number;
  petBId: number;
}
