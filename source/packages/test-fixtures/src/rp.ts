export interface FixtureRpCharacter {
  id: number;
  user_id: number;
  name: string;
  faction_id: number | null;
  job_id: number | null;
  cash: number;
  bank_balance: number;
  is_wanted: boolean;
  criminal_record: number;
  health: number;
  in_prison: boolean;
}

export const FIXTURE_RP_CHARACTERS: FixtureRpCharacter[] = [
  {
    id: 1,
    user_id: 1,
    name: 'Alex Novak',
    faction_id: null,
    job_id: 1,
    cash: 500,
    bank_balance: 2000,
    is_wanted: false,
    criminal_record: 0,
    health: 100,
    in_prison: false,
  },
  {
    id: 2,
    user_id: 2,
    name: 'Officer Morgan',
    faction_id: 1, // Police
    job_id: 2,
    cash: 800,
    bank_balance: 5000,
    is_wanted: false,
    criminal_record: 0,
    health: 100,
    in_prison: false,
  },
  {
    id: 3,
    user_id: 4,
    name: 'Criminal Pete',
    faction_id: 2, // Criminal faction
    job_id: null,
    cash: 200,
    bank_balance: 100,
    is_wanted: true,
    criminal_record: 3,
    health: 75,
    in_prison: false,
  },
];

export const FIXTURE_RP_CHARACTER = FIXTURE_RP_CHARACTERS[0];

export const FIXTURE_RP_FACTIONS = [
  { id: 1, name: 'Nutropolis PD', type: 'law_enforcement', member_count: 5 },
  { id: 2, name: 'The Underground', type: 'criminal', member_count: 8 },
  { id: 3, name: 'City Hall', type: 'government', member_count: 3 },
];

export const FIXTURE_RP_BANK_TRANSACTION = {
  id: 1,
  character_id: 1,
  type: 'DEPOSIT',
  amount: 500,
  idempotency_key: '00000000-0000-0000-0000-000000000010',
  balance_before: 1500,
  balance_after: 2000,
};
