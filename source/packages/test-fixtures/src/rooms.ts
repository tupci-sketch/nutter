export interface FixtureRoom {
  id: number;
  owner_id: number;
  name: string;
  model: string;
  access_mode: 'open' | 'doorbell' | 'password' | 'invisible';
  max_users: number;
  description: string;
}

export const FIXTURE_ROOMS: FixtureRoom[] = [
  {
    id: 1,
    owner_id: 1,
    name: 'Test Room',
    model: 'model_a',
    access_mode: 'open',
    max_users: 25,
    description: 'A basic test room',
  },
  {
    id: 2,
    owner_id: 1,
    name: 'Locked Room',
    model: 'model_b',
    access_mode: 'password',
    max_users: 10,
    description: 'Password-protected room',
  },
  {
    id: 3,
    owner_id: 2,
    name: 'Staff Lounge',
    model: 'model_c',
    access_mode: 'doorbell',
    max_users: 50,
    description: 'Staff-only room fixture',
  },
];

export const FIXTURE_ROOM = FIXTURE_ROOMS[0];
