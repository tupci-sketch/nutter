export interface FixtureTransaction {
  id: number;
  user_id: number;
  type: string;
  amount: number;
  currency: 'credits' | 'diamonds' | 'nut_points' | 'seasonal';
  idempotency_key: string;
  transaction_hash: string;
  source: string;
  balance_before: number;
  balance_after: number;
}

export const FIXTURE_TRANSACTIONS: FixtureTransaction[] = [
  {
    id: 1,
    user_id: 1,
    type: 'PURCHASE',
    amount: -50,
    currency: 'credits',
    idempotency_key: '00000000-0000-0000-0000-000000000001',
    transaction_hash: 'abc123fixture',
    source: 'catalogue',
    balance_before: 550,
    balance_after: 500,
  },
  {
    id: 2,
    user_id: 1,
    type: 'GRANT',
    amount: 100,
    currency: 'credits',
    idempotency_key: '00000000-0000-0000-0000-000000000002',
    transaction_hash: 'def456fixture',
    source: 'admin',
    balance_before: 400,
    balance_after: 500,
  },
];

export const FIXTURE_CATALOGUE_ITEM = {
  id: 1,
  page_id: 1,
  item_ids: [1],
  name: 'Test Chair',
  credits_cost: 10,
  diamonds_cost: 0,
  amount: 1,
};

export const FIXTURE_TRANSACTION = FIXTURE_TRANSACTIONS[0];
