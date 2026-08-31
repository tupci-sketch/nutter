export interface FixtureUser {
  id: number;
  username: string;
  email: string;
  rank: number;
  credits: number;
  diamonds: number;
  nut_points: number;
  look: string;
  motto: string;
}

export const FIXTURE_USERS: FixtureUser[] = [
  {
    id: 1,
    username: 'testuser',
    email: 'testuser@habnut.test',
    rank: 1,
    credits: 500,
    diamonds: 10,
    nut_points: 100,
    look: 'hd-180-1.ch-210-66.lg-270-110.sh-290-62',
    motto: 'Test user fixture',
  },
  {
    id: 2,
    username: 'staffmember',
    email: 'staff@habnut.test',
    rank: 5,
    credits: 1000,
    diamonds: 50,
    nut_points: 500,
    look: 'hd-180-7.ch-210-66.lg-270-110.sh-290-62',
    motto: 'Staff fixture',
  },
  {
    id: 3,
    username: 'adminuser',
    email: 'admin@habnut.test',
    rank: 7,
    credits: 9999,
    diamonds: 999,
    nut_points: 9999,
    look: 'hd-180-3.ch-210-66.lg-270-110.sh-290-62',
    motto: 'Admin fixture',
  },
  {
    id: 4,
    username: 'banneduser',
    email: 'banned@habnut.test',
    rank: 1,
    credits: 0,
    diamonds: 0,
    nut_points: 0,
    look: 'hd-180-1.ch-210-66.lg-270-110.sh-290-62',
    motto: 'Banned fixture',
  },
];

export const FIXTURE_USER = FIXTURE_USERS[0];
export const FIXTURE_STAFF = FIXTURE_USERS[1];
export const FIXTURE_ADMIN = FIXTURE_USERS[2];
export const FIXTURE_BANNED_USER = FIXTURE_USERS[3];
