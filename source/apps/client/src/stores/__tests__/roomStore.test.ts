import { describe, it, expect, beforeEach, vi } from 'vitest';

/**
 * Reading what the hotel actually sends.
 *
 * Every payload below is the shape the server builds in RoomHandler and Room —
 * batched moves, instance ids rather than user ids, `rotation` rather than
 * `dir`. The store used to expect its own invented shapes, so a room arrived
 * and nothing in it was drawn.
 */

type Handler = (payload: unknown) => void;
const handlers = new Map<string, Handler>();
const sent: Array<{ type: string; payload: unknown }> = [];

vi.mock('@/ws/WsClient', () => ({
  getWsClient: () => ({
    on(type: string, handler: Handler) {
      handlers.set(type, handler);
      return () => handlers.delete(type);
    },
    send(type: string, payload: unknown) {
      sent.push({ type, payload });
    },
  }),
}));

const { useRoomStore, initRoomListeners, setSelfUserId } = await import('../roomStore');

function deliver(type: string, payload: unknown): void {
  const handler = handlers.get(type);
  if (!handler) throw new Error(`Nothing is listening for ${type}`);
  handler(payload);
}

const ENTER_SUCCESS = {
  roomId: 5,
  name: 'The White House',
  description: 'A grand place',
  modelId: 'model_a',
  heightmap: 'xxxx\nxxxx',
  doorX: 1,
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
  entities: [
    {
      instanceId: 101, type: 'player', sourceId: 7, name: 'tupci',
      figureString: 'hd-180-1', x: 2, y: 3, z: 0, rotation: 4,
    },
    {
      instanceId: 102, type: 'player', sourceId: 9, name: 'someone',
      figureString: 'hd-185-2', x: 5, y: 5, z: 0, rotation: 0,
    },
  ],
  floorItems: [
    { id: 20, baseId: 3, spriteId: 'chair', x: 1, y: 1, z: 0, rotation: 2, state: 0, extra: '' },
  ],
  wallItems: [
    { id: 30, baseId: 4, spriteId: 'poster', wallPosition: ':w=2,1 l=3,2 l', state: 0, extra: '' },
  ],
};

beforeEach(() => {
  handlers.clear();
  sent.length = 0;
  setSelfUserId(null);
  useRoomStore.setState({
    currentRoom: null,
    entities: new Map(),
    floorItems: new Map(),
    wallItems: new Map(),
    chat: [],
    selfInstanceId: null,
    error: null,
  });
  initRoomListeners();
});

describe('walking into a room', () => {
  it('keeps the floor it was given, so there is something to stand on', () => {
    deliver('room.enter.success', ENTER_SUCCESS);

    const room = useRoomStore.getState().currentRoom;
    expect(room?.id).toBe(5);
    expect(room?.heightmap).toBe('xxxx\nxxxx');
    expect(room?.name).toBe('The White House');
  });

  it('keeps everybody standing in it', () => {
    deliver('room.enter.success', ENTER_SUCCESS);

    const entities = useRoomStore.getState().entities;
    expect(entities.size).toBe(2);
    expect(entities.get(101)?.name).toBe('tupci');
    expect(entities.get(102)?.rotation).toBe(0);
  });

  it('keeps the furniture that was already there', () => {
    deliver('room.enter.success', ENTER_SUCCESS);

    expect(useRoomStore.getState().floorItems.get(20)?.spriteId).toBe('chair');
    expect(useRoomStore.getState().wallItems.get(30)?.wallPosition).toBe(':w=2,1 l=3,2 l');
  });

  it('picks our own figure out of the room', () => {
    setSelfUserId(7);
    deliver('room.enter.success', ENTER_SUCCESS);

    expect(useRoomStore.getState().selfInstanceId).toBe(101);
  });

  it('leaves the camera unattached when we are not among them', () => {
    setSelfUserId(999);
    deliver('room.enter.success', ENTER_SUCCESS);

    expect(useRoomStore.getState().selfInstanceId).toBeNull();
  });

  it('says so when the hotel turns us away', () => {
    deliver('room.enter.error', { code: 'room.full', message: 'Room is full' });

    expect(useRoomStore.getState().error).toBe('Room is full');
  });
});

describe('people moving', () => {
  beforeEach(() => deliver('room.enter.success', ENTER_SUCCESS));

  it('applies a whole tick of movement at once', () => {
    deliver('room.user.moved', {
      moves: [
        { instanceId: 101, x: 3, y: 3, z: 0, rotation: 2 },
        { instanceId: 102, x: 5, y: 6, z: 0.5, rotation: 4 },
      ],
    });

    const entities = useRoomStore.getState().entities;
    expect(entities.get(101)).toMatchObject({ x: 3, y: 3, rotation: 2 });
    expect(entities.get(102)).toMatchObject({ x: 5, y: 6, z: 0.5, rotation: 4 });
  });

  it('ignores a figure that has already left', () => {
    deliver('room.user.moved', { moves: [{ instanceId: 999, x: 1, y: 1, z: 0, rotation: 0 }] });

    expect(useRoomStore.getState().entities.size).toBe(2);
  });

  it('adds somebody who walks in', () => {
    deliver('room.user.entered', {
      instanceId: 103, userId: 11, username: 'newcomer',
      figureString: 'hd-190-3', x: 0, y: 0, z: 0, rotation: 2,
    });

    expect(useRoomStore.getState().entities.get(103)?.name).toBe('newcomer');
  });

  it('removes somebody who leaves', () => {
    deliver('room.user.left', { instanceId: 102 });

    expect(useRoomStore.getState().entities.has(102)).toBe(false);
  });
});

describe('chat', () => {
  beforeEach(() => deliver('room.enter.success', ENTER_SUCCESS));

  it('puts a name to a line of speech', () => {
    deliver('room.user.chat', { instanceId: 102, message: 'hello', colour: 0 });

    const [line] = useRoomStore.getState().chat;
    expect(line).toMatchObject({ name: 'someone', message: 'hello', kind: 'talk' });
  });

  it('tells a shout from a whisper', () => {
    deliver('room.user.shout', { instanceId: 102, message: 'OI', colour: 0 });
    deliver('room.user.whisper', { instanceId: 101, message: 'psst' });

    const kinds = useRoomStore.getState().chat.map(l => l.kind);
    expect(kinds).toEqual(['shout', 'whisper']);
  });

  it('keeps the backlog from growing without limit', () => {
    for (let i = 0; i < 150; i++) {
      deliver('room.user.chat', { instanceId: 101, message: `line ${i}` });
    }

    const chat = useRoomStore.getState().chat;
    expect(chat).toHaveLength(100);
    expect(chat[chat.length - 1].message).toBe('line 149');
  });
});

describe('furniture', () => {
  beforeEach(() => deliver('room.enter.success', ENTER_SUCCESS));

  it('shows an item somebody puts down', () => {
    deliver('furni.placed', {
      id: 21, baseId: 5, spriteId: 'table', x: 2, y: 2, z: 0, rotation: 0, state: 0, extra: '',
    });

    expect(useRoomStore.getState().floorItems.get(21)?.spriteId).toBe('table');
  });

  it('follows an item being moved', () => {
    deliver('furni.moved', { id: 20, x: 4, y: 4, z: 0, rotation: 6 });

    expect(useRoomStore.getState().floorItems.get(20)).toMatchObject({ x: 4, y: 4, rotation: 6 });
  });

  it('takes away an item that is picked up', () => {
    deliver('furni.picked_up', { itemId: 20 });

    expect(useRoomStore.getState().floorItems.has(20)).toBe(false);
  });

  it('follows a switch being flipped', () => {
    deliver('furni.state.changed', { id: 20, state: 1 });

    expect(useRoomStore.getState().floorItems.get(20)?.state).toBe(1);
  });
});

describe('what the client asks for', () => {
  it('asks to enter by the name the hotel registered', () => {
    useRoomStore.getState().enterRoom(5);

    expect(sent).toContainEqual({ type: 'room.enter', payload: { roomId: 5 } });
  });

  it('sends chat and movement the hotel will act on', () => {
    useRoomStore.getState().move(3, 4);
    useRoomStore.getState().sendChat('hello');

    expect(sent).toContainEqual({ type: 'room.move', payload: { x: 3, y: 4 } });
    expect(sent).toContainEqual({ type: 'room.chat', payload: { message: 'hello', colour: 0 } });
  });
});
