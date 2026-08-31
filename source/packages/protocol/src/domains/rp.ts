export const RpPacketTypes = {
  // c2s characters
  CHARACTER_CREATE: 'rp.character.create',
  CHARACTER_INFO: 'rp.character.info',
  CHARACTER_UPDATE: 'rp.character.update',

  // c2s factions
  FACTION_LIST: 'rp.faction.list',
  FACTION_INFO: 'rp.faction.info',
  FACTION_APPLY: 'rp.faction.apply',
  FACTION_RESIGN: 'rp.faction.resign',

  // c2s vehicles
  VEHICLE_LIST: 'rp.vehicle.list',
  VEHICLE_SPAWN: 'rp.vehicle.spawn',
  VEHICLE_DESPAWN: 'rp.vehicle.despawn',
  VEHICLE_DRIVE: 'rp.vehicle.drive',

  // c2s properties
  PROPERTY_LIST: 'rp.property.list',
  PROPERTY_BUY: 'rp.property.buy',
  PROPERTY_SELL: 'rp.property.sell',
  PROPERTY_ACCESS: 'rp.property.access',

  // c2s jobs / employment
  JOB_LIST: 'rp.job.list',
  JOB_APPLY: 'rp.job.apply',
  JOB_RESIGN: 'rp.job.resign',
  SHIFT_CLOCK_IN: 'rp.shift.clock_in',
  SHIFT_CLOCK_OUT: 'rp.shift.clock_out',

  // c2s businesses
  BUSINESS_CREATE: 'rp.business.create',
  BUSINESS_INFO: 'rp.business.info',
  BUSINESS_UPDATE: 'rp.business.update',
  BUSINESS_HIRE: 'rp.business.hire',
  BUSINESS_FIRE: 'rp.business.fire',

  // c2s bank
  BANK_BALANCE: 'rp.bank.balance',
  BANK_DEPOSIT: 'rp.bank.deposit',
  BANK_WITHDRAW: 'rp.bank.withdraw',
  BANK_TRANSFER: 'rp.bank.transfer',
  BANK_HISTORY: 'rp.bank.history',

  // c2s crimes / law
  CRIME_REPORT: 'rp.crime.report',
  ARREST: 'rp.arrest',
  PROCESS: 'rp.process',
  SENTENCE: 'rp.sentence',
  RELEASE: 'rp.release',
  APPEAL: 'rp.appeal',

  // c2s government / elections
  ELECTION_LIST: 'rp.election.list',
  ELECTION_NOMINATE: 'rp.election.nominate',
  ELECTION_VOTE: 'rp.election.vote',
  GOVERNMENT_DECREE: 'rp.government.decree',
  GOVERNMENT_LAW_LIST: 'rp.government.law.list',

  // c2s dispatch / medical
  DISPATCH_CALL: 'rp.dispatch.call',
  DISPATCH_ACCEPT: 'rp.dispatch.accept',
  DISPATCH_RESOLVE: 'rp.dispatch.resolve',
  MEDICAL_TREAT: 'rp.medical.treat',
  MEDICAL_ADMIT: 'rp.medical.admit',
  MEDICAL_DISCHARGE: 'rp.medical.discharge',

  // c2s scenes / crafting
  SCENE_START: 'rp.scene.start',
  SCENE_END: 'rp.scene.end',
  CRAFTING_RECIPES: 'rp.crafting.recipes',
  CRAFTING_CRAFT: 'rp.crafting.craft',

  // c2s licences
  LICENCE_LIST: 'rp.licence.list',
  LICENCE_ACQUIRE: 'rp.licence.acquire',
  LICENCE_TEST: 'rp.licence.test',

  // s2c
  CHARACTER_CREATED: 'rp.character.created',
  CHARACTER_INFO_RESULT: 'rp.character.info.result',
  CHARACTER_UPDATED: 'rp.character.updated',

  FACTION_LIST_RESULT: 'rp.faction.list.result',
  FACTION_INFO_RESULT: 'rp.faction.info.result',
  FACTION_APPLIED: 'rp.faction.applied',
  FACTION_ACCEPTED: 'rp.faction.accepted',
  FACTION_RESIGNED: 'rp.faction.resigned',

  VEHICLE_LIST_RESULT: 'rp.vehicle.list.result',
  VEHICLE_SPAWNED: 'rp.vehicle.spawned',
  VEHICLE_MOVED: 'rp.vehicle.moved',

  PROPERTY_LIST_RESULT: 'rp.property.list.result',
  PROPERTY_BOUGHT: 'rp.property.bought',
  PROPERTY_SOLD: 'rp.property.sold',

  JOB_LIST_RESULT: 'rp.job.list.result',
  JOB_APPLIED: 'rp.job.applied',
  JOB_RESIGNED: 'rp.job.resigned',
  SHIFT_STARTED: 'rp.shift.started',
  SHIFT_ENDED: 'rp.shift.ended',
  PAYROLL_PAID: 'rp.payroll.paid',

  BANK_BALANCE_RESULT: 'rp.bank.balance.result',
  BANK_TRANSACTION: 'rp.bank.transaction',
  BANK_HISTORY_RESULT: 'rp.bank.history.result',

  ARRESTED: 'rp.arrested',
  PROCESSED: 'rp.processed',
  SENTENCED: 'rp.sentenced',
  RELEASED: 'rp.released',
  CRIMINAL_RECORD_UPDATED: 'rp.criminal_record.updated',

  ELECTION_LIST_RESULT: 'rp.election.list.result',
  ELECTION_RESULT: 'rp.election.result',
  GOVERNMENT_DECREE_ISSUED: 'rp.government.decree.issued',
  GOVERNMENT_LAW_LIST_RESULT: 'rp.government.law.list.result',

  DISPATCH_CALL_RECEIVED: 'rp.dispatch.call.received',
  DISPATCH_ACCEPTED: 'rp.dispatch.accepted',
  DISPATCH_RESOLVED: 'rp.dispatch.resolved',
  MEDICAL_TREATED: 'rp.medical.treated',
  MEDICAL_ADMITTED: 'rp.medical.admitted',
  MEDICAL_DISCHARGED: 'rp.medical.discharged',

  SCENE_STARTED: 'rp.scene.started',
  SCENE_ENDED: 'rp.scene.ended',
  CRAFTING_RECIPES_RESULT: 'rp.crafting.recipes.result',
  CRAFTED: 'rp.crafted',

  LICENCE_LIST_RESULT: 'rp.licence.list.result',
  LICENCE_ACQUIRED: 'rp.licence.acquired',
  LICENCE_TEST_RESULT: 'rp.licence.test.result',

  // Faction money: territory income, heist takings, wages and fines.
  TREASURY_VIEW: 'rp.treasury.view',
  TREASURY_RESULT: 'rp.treasury.result',
  TREASURY_DEPOSIT: 'rp.treasury.deposit',
  TREASURY_WITHDRAW: 'rp.treasury.withdraw',

  // Heists: a crew, a target, an alarm, and whoever gets there first.
  HEIST_TARGETS: 'rp.heist.targets',
  HEIST_TARGETS_RESULT: 'rp.heist.targets.result',
  HEIST_PLAN: 'rp.heist.plan',
  HEIST_JOIN: 'rp.heist.join',
  HEIST_LEAVE: 'rp.heist.leave',
  HEIST_START: 'rp.heist.start',
  HEIST_FOIL: 'rp.heist.foil',
  HEIST_ACTIVE: 'rp.heist.active',
  HEIST_ACTIVE_RESULT: 'rp.heist.active.result',
  HEIST_UPDATED: 'rp.heist.updated',
  HEIST_ALARM: 'rp.heist.alarm',
  HEIST_RESOLVED: 'rp.heist.resolved',

  ERROR: 'rp.error',
} as const;

export type RpPacketType = (typeof RpPacketTypes)[keyof typeof RpPacketTypes];

export type RpFactionTag = 'police' | 'fire' | 'medical' | 'government' | 'criminal' | 'civilian' | 'business' | 'media';

export interface RpCharacter {
  id: number;
  userId: number;
  name: string;
  surname: string;
  age: number;
  biography: string;
  factionId: number | null;
  factionTag: RpFactionTag | null;
  jobId: number | null;
  jobTitle: string | null;
  cashBalance: number;
  bankBalance: number;
  criminalRecord: RpCrime[];
  licences: string[];
  health: number;
  prisonExpiry: string | null;
  createdAt: string;
}

export interface RpCrime {
  id: number;
  type: string;
  description: string;
  arrestedBy: number;
  arrestedByName: string;
  sentence: string | null;
  recordedAt: string;
  expungedAt: string | null;
}

export interface RpJob {
  id: number;
  title: string;
  description: string;
  factionId: number | null;
  factionName: string | null;
  salary: number;
  salaryInterval: 'shift' | 'daily' | 'weekly';
  requirements: string[];
  openings: number;
}

export interface RpProperty {
  id: number;
  name: string;
  type: 'residence' | 'business' | 'government' | 'land';
  price: number;
  rentPerWeek: number | null;
  ownerId: number | null;
  ownerName: string | null;
  roomId: number;
  address: string;
  forSale: boolean;
}

export interface RpBankBalanceResultPayload {
  cashBalance: number;
  bankBalance: number;
}

export interface RpBankDepositPayload {
  amount: number;
  idempotencyKey: string;
}

export interface RpBankWithdrawPayload {
  amount: number;
  idempotencyKey: string;
}

export interface RpBankTransferPayload {
  toCharacterId: number;
  amount: number;
  description: string;
  idempotencyKey: string;
}

export interface RpDispatchCallPayload {
  callType: 'police' | 'fire' | 'medical';
  location: string;
  description: string;
  priority: 1 | 2 | 3;
}

export interface RpCraftingCraftPayload {
  recipeId: string;
  idempotencyKey: string;
}

export interface RpElectionVotePayload {
  electionId: number;
  candidateCharacterId: number;
  idempotencyKey: string;
}

/**
 * Money a faction holds together.
 *
 * Territory income and heist takings land here; wages and fines come out. Every
 * movement is recorded, because a faction's members will argue about where the
 * money went and the answer should not depend on somebody's memory.
 */
export interface RpTreasuryEntry {
  id: number;
  /** Signed: a withdrawal is negative, so the entries sum to the balance. */
  amount: number;
  balanceAfter: number;
  kind: 'turf_income' | 'heist' | 'deposit' | 'withdrawal' | 'fine' | 'payroll';
  memo: string;
  actorCharacterId: number | null;
  createdAt: string;
}

export interface RpTreasuryResultPayload {
  factionId: number;
  balance: number;
  history: RpTreasuryEntry[];
}

/** Something that can be robbed, and whether it can be robbed right now. */
export interface RpHeistTarget {
  id: number;
  code: string;
  name: string;
  description: string;
  roomId: number | null;
  minCrew: number;
  maxCrew: number;
  durationSeconds: number;
  /** How long into the job before the police hear about it. */
  alarmSeconds: number;
  payoutMin: number;
  payoutMax: number;
  /** Police who must be on duty before it will open at all. */
  policeRequired: number;
  cooldownMinutes: number;
  available: boolean;
  /** Why it cannot be attempted, when it cannot. */
  unavailableReason: string | null;
}

export type RpHeistState = 'planning' | 'in_progress' | 'succeeded' | 'foiled' | 'abandoned';

export interface RpHeist {
  id: number;
  targetId: number;
  targetName: string;
  factionId: number;
  leaderCharacterId: number;
  state: RpHeistState;
  startedAt: string | null;
  alarmAt: string | null;
  resolvesAt: string | null;
  payout: number;
  crewSize: number;
}

/** Raised to police once a job's alarm has gone. */
export interface RpHeistAlarmPayload {
  heistId: number;
  targetName: string;
  roomId: number | null;
  crewSize: number;
  /** When the crew get away with it if nobody arrives. */
  resolvesAt: string;
}

export interface RpHeistResolvedPayload {
  heistId: number;
  outcome: 'succeeded' | 'foiled';
  payout: number;
  factionShare: number;
  crewShare: number;
}
