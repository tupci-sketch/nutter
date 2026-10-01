import type { RoomEntity, FloorItem, RoomInfo } from '@/stores/roomStore';

/**
 * Describing a room in words.
 *
 * An isometric canvas is a picture with nothing underneath it, so a player
 * using a screen reader has no idea who is in the room, where they are, or what
 * is in it. This turns the same state the renderer draws into a sentence, which
 * is the difference between the hotel being usable and being a blank rectangle.
 *
 * Kept free of React and of the renderer so the wording can be tested on its
 * own — it is the part that has to be right, and it is easy to get wrong in
 * ways nobody sees.
 */

/** Which way a direction faces, in words rather than a number. */
const COMPASS = [
  'north', 'north-east', 'east', 'south-east',
  'south', 'south-west', 'west', 'north-west',
] as const;

export function facing(direction: number): string {
  return COMPASS[((direction % 8) + 8) % 8];
}

/** Joins a list the way a person would say it. */
export function readAsList(items: readonly string[]): string {
  if (items.length === 0) return '';
  if (items.length === 1) return items[0];
  return `${items.slice(0, -1).join(', ')} and ${items[items.length - 1]}`;
}

/** How many people are here, said naturally. */
export function countPeople(count: number): string {
  if (count === 0) return 'Nobody else is here';
  if (count === 1) return '1 other person is here';
  return `${count} other people are here`;
}

export interface RoomDescriptionInput {
  room: RoomInfo | null;
  users: readonly RoomEntity[];
  furni: readonly FloorItem[];
  /** The viewer's own figure, so they are not described as somebody else. */
  selfInstanceId: number | null;
}

/**
 * A sentence or two describing the room as it stands.
 *
 * Deliberately short. A description read out in full every time somebody moves
 * would be unusable, so this says who is here and what is here, and leaves
 * where everything is to the position readout for one person at a time.
 */
export function describeRoom({ room, users, furni, selfInstanceId }: RoomDescriptionInput): string {
  if (!room) return 'No room loaded.';

  const others = users.filter((u) => u.instanceId !== selfInstanceId);
  const parts: string[] = [`${room.name}, a room by ${room.ownerName}.`];

  parts.push(`${countPeople(others.length)}.`);

  if (others.length > 0 && others.length <= 8) {
    parts.push(`${readAsList(others.map((u) => u.name))}.`);
  }

  if (furni.length > 0) {
    const kinds = new Set(furni.map((f) => f.spriteId));
    parts.push(
      furni.length === 1
        ? 'There is one piece of furniture.'
        : `There are ${furni.length} pieces of furniture, of ${kinds.size} ` +
          `${kinds.size === 1 ? 'kind' : 'kinds'}.`,
    );
  }

  return parts.join(' ');
}

/** Where the viewer is standing, and which way they are looking. */
export function describePosition(user: RoomEntity | undefined): string {
  if (!user) return 'You are not in the room.';
  return `You are at ${user.x}, ${user.y}, facing ${facing(user.rotation)}.`;
}

/** What to say when somebody arrives or leaves. */
export function describeArrival(username: string, arrived: boolean): string {
  return arrived ? `${username} came in.` : `${username} left.`;
}

/** What to say about a tile the player is about to walk to. */
export function describeMove(x: number, y: number, blocked: boolean): string {
  return blocked ? `Cannot walk to ${x}, ${y}.` : `Walking to ${x}, ${y}.`;
}
