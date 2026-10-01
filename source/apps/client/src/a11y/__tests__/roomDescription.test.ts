import { describe, it, expect } from 'vitest';
import {
  countPeople,
  describeArrival,
  describeMove,
  describePosition,
  describeRoom,
  facing,
  readAsList,
} from '../roomDescription';
import type { FloorItem, RoomEntity, RoomInfo } from '@/stores/roomStore';

/**
 * What the room sounds like.
 *
 * An isometric canvas is a picture with nothing underneath it, so this wording
 * is the whole of the room for a player using a screen reader. It has to be
 * accurate, and it has to be short enough to sit through.
 */

const room: RoomInfo = {
  id: 1,
  name: 'The White House',
  description: 'A grand place',
  modelId: 'model_a',
  heightmap: 'xxxx\nxxxx',
  doorX: 0,
  doorY: 0,
  doorRotation: 2,
  maxVisitors: 25,
  ownerId: 7,
  ownerName: 'tupci',
  wallpaper: '0.0',
  floorPattern: '0.0',
  landscape: '0.0',
  background: '0.0',
  hideWalls: false,
  wallHeight: 0,
  wallThickness: '0',
  floorThickness: '0',
};

function user(instanceId: number, name: string, over: Partial<RoomEntity> = {}): RoomEntity {
  return {
    instanceId,
    type: 'player',
    sourceId: instanceId,
    name,
    figureString: 'hd-180-1',
    x: 3, y: 4, z: 0, rotation: 2,
    ...over,
  };
}

function furni(id: number, spriteId: string): FloorItem {
  return { id, baseId: id, spriteId, x: 0, y: 0, z: 0, rotation: 0, state: 0, extra: '' };
}

describe('facing', () => {
  it('names each direction', () => {
    expect(facing(0)).toBe('north');
    expect(facing(2)).toBe('east');
    expect(facing(4)).toBe('south');
    expect(facing(6)).toBe('west');
  });

  it('wraps, in both directions', () => {
    expect(facing(8)).toBe('north');
    expect(facing(-1)).toBe('north-west');
  });
});

describe('readAsList', () => {
  it('reads a list the way a person would say it', () => {
    expect(readAsList([])).toBe('');
    expect(readAsList(['tupci'])).toBe('tupci');
    expect(readAsList(['tupci', 'ana'])).toBe('tupci and ana');
    expect(readAsList(['tupci', 'ana', 'jo'])).toBe('tupci, ana and jo');
  });
});

describe('countPeople', () => {
  it('gets the grammar right at every size', () => {
    expect(countPeople(0)).toBe('Nobody else is here');
    expect(countPeople(1)).toBe('1 other person is here');
    expect(countPeople(4)).toBe('4 other people are here');
  });
});

describe('describeRoom', () => {
  it('names the room and its owner', () => {
    const text = describeRoom({ room, users: [], furni: [], selfInstanceId: null });
    expect(text).toContain('The White House');
    expect(text).toContain('tupci');
  });

  it('does not count the listener as somebody else in the room', () => {
    const text = describeRoom({
      room,
      users: [user(7, 'tupci'), user(9, 'ana')],
      furni: [],
      selfInstanceId: 7,
    });
    expect(text).toContain('1 other person is here');
    expect(text).toContain('ana');
    expect(text).not.toContain('tupci, ');
  });

  it('names the people when there are few enough to be worth naming', () => {
    const text = describeRoom({
      room,
      users: [user(1, 'ana'), user(2, 'jo')],
      furni: [],
      selfInstanceId: null,
    });
    expect(text).toContain('ana and jo');
  });

  it('stops naming people once the list would be unbearable', () => {
    const crowd = Array.from({ length: 20 }, (_, i) => user(i, `player${i}`));
    const text = describeRoom({ room, users: crowd, furni: [], selfInstanceId: null });

    expect(text).toContain('20 other people are here');
    expect(text).not.toContain('player7');
  });

  it('summarises the furniture rather than listing every piece', () => {
    const text = describeRoom({
      room,
      users: [],
      furni: [furni(1, 'chair'), furni(2, 'chair'), furni(3, 'table')],
      selfInstanceId: null,
    });
    expect(text).toContain('3 pieces of furniture');
    expect(text).toContain('2 kinds');
  });

  it('gets the singular right for one piece', () => {
    const text = describeRoom({ room, users: [], furni: [furni(1, 'chair')], selfInstanceId: null });
    expect(text).toContain('one piece of furniture');
  });

  it('says nothing about furniture in an empty room', () => {
    const text = describeRoom({ room, users: [], furni: [], selfInstanceId: null });
    expect(text).not.toContain('furniture');
  });

  it('says so plainly when there is no room', () => {
    expect(describeRoom({ room: null, users: [], furni: [], selfInstanceId: null }))
      .toBe('No room loaded.');
  });
});

describe('describePosition', () => {
  it('says where you are and which way you are looking', () => {
    expect(describePosition(user(1, 'tupci', { x: 5, y: 9, rotation: 4 })))
      .toBe('You are at 5, 9, facing south.');
  });

  it('says so when you are not in a room', () => {
    expect(describePosition(undefined)).toBe('You are not in the room.');
  });
});

describe('event wording', () => {
  it('says who came and went', () => {
    expect(describeArrival('ana', true)).toBe('ana came in.');
    expect(describeArrival('ana', false)).toBe('ana left.');
  });

  it('says where you are walking, or why you are not', () => {
    expect(describeMove(3, 4, false)).toBe('Walking to 3, 4.');
    expect(describeMove(3, 4, true)).toBe('Cannot walk to 3, 4.');
  });
});
