import { create } from 'zustand';
import { getWsClient } from '@/ws/WsClient';
import { Packet } from '@/protocol/packets';

/**
 * Which user we are signed in as.
 *
 * Needed to pick our own figure out of the room on arrival. It is kept here
 * rather than read from the auth store because the two stores would otherwise
 * import each other; sign-in pushes the id in instead.
 */
let selfUserId: number | null = null;

/** Told by the auth store as sign-in completes, and cleared on sign-out. */
export function setSelfUserId(userId: number | null): void {
  selfUserId = userId;
}

/**
 * One figure standing in the room.
 *
 * The hotel identifies everything in a room by an instance id, not a user id:
 * pets and bots stand in rooms too, and the same person can appear in two
 * rooms at once on two devices. Everything that happens afterwards — moving,
 * speaking, dancing — refers to that instance id, so it is what we key on.
 */
export interface RoomEntity {
  instanceId: number;
  /** 'player' | 'pet' | 'bot' — what kind of thing this is. */
  type: string;
  /** The user, pet or bot id behind the figure. */
  sourceId: number;
  name: string;
  figureString: string;
  x: number;
  y: number;
  z: number;
  rotation: number;
}

export interface FloorItem {
  id: number;
  baseId: number;
  spriteId: string;
  x: number;
  y: number;
  z: number;
  rotation: number;
  state: number;
  extra: string;
}

export interface WallItem {
  id: number;
  baseId: number;
  spriteId: string;
  wallPosition: string;
  state: number;
  extra: string;
}

/** The room itself: its shape, its door, and how its owner decorated it. */
export interface RoomInfo {
  id: number;
  name: string;
  description: string;
  modelId: string;
  /** Rows of tile heights, newline separated: the floor the room is built on. */
  heightmap: string;
  doorX: number;
  doorY: number;
  doorRotation: number;
  maxVisitors: number;
  ownerId: number;
  ownerName: string;
  wallpaper: string;
  floorPattern: string;
  landscape: string;
  background: string;
  hideWalls: boolean;
  wallHeight: number;
  wallThickness: string;
  floorThickness: string;
}

export interface ChatLine {
  instanceId: number;
  name: string;
  message: string;
  /** 'talk' | 'shout' | 'whisper' — how it should be shown. */
  kind: 'talk' | 'shout' | 'whisper';
  colour: number;
  ts: number;
}

export interface RoomStore {
  currentRoom: RoomInfo | null;
  entities: Map<number, RoomEntity>;
  floorItems: Map<number, FloorItem>;
  wallItems: Map<number, WallItem>;
  chat: ChatLine[];
  /** Our own figure's instance id, so the camera knows who to follow. */
  selfInstanceId: number | null;
  error: string | null;

  enterRoom: (roomId: number) => void;
  leaveRoom: () => void;
  move: (x: number, y: number) => void;
  look: (x: number, y: number) => void;
  sendChat: (message: string, colour?: number) => void;
  sendShout: (message: string, colour?: number) => void;
  sendWhisper: (toInstanceId: number, message: string) => void;
  dance: (danceId: number) => void;
  wave: (signId: number) => void;
}

const MAX_CHAT_LINES = 100;

export const useRoomStore = create<RoomStore>(() => ({
  currentRoom: null,
  entities: new Map(),
  floorItems: new Map(),
  wallItems: new Map(),
  chat: [],
  selfInstanceId: null,
  error: null,

  enterRoom(roomId) {
    getWsClient().send(Packet.ROOM_ENTER, { roomId });
  },

  leaveRoom() {
    getWsClient().send(Packet.ROOM_LEAVE, {});
    useRoomStore.setState({
      currentRoom: null,
      entities: new Map(),
      floorItems: new Map(),
      wallItems: new Map(),
      chat: [],
      selfInstanceId: null,
    });
  },

  move(x, y) {
    getWsClient().send(Packet.ROOM_MOVE, { x, y });
  },

  look(x, y) {
    getWsClient().send(Packet.ROOM_LOOK, { x, y });
  },

  sendChat(message, colour = 0) {
    getWsClient().send(Packet.ROOM_CHAT, { message, colour });
  },

  sendShout(message, colour = 0) {
    getWsClient().send(Packet.ROOM_SHOUT, { message, colour });
  },

  sendWhisper(toInstanceId, message) {
    getWsClient().send(Packet.ROOM_WHISPER, { instanceId: toInstanceId, message });
  },

  dance(danceId) {
    getWsClient().send(Packet.ROOM_USER_DANCE, { danceId });
  },

  wave(signId) {
    getWsClient().send(Packet.ROOM_USER_SIGN, { signId });
  },
}));

/** Add a line of speech, keeping only the recent backlog. */
function pushChat(line: Omit<ChatLine, 'ts'>): void {
  useRoomStore.setState(s => ({
    chat: [...s.chat.slice(-(MAX_CHAT_LINES - 1)), { ...line, ts: Date.now() }],
  }));
}

/** Name to show against a line of speech, when we know the figure. */
function nameFor(instanceId: number): string {
  return useRoomStore.getState().entities.get(instanceId)?.name ?? '';
}

export function initRoomListeners(): void {
  const ws = getWsClient();

  ws.on(Packet.ROOM_ENTER_SUCCESS, raw => {
    const p = raw as RoomInfo & {
      roomId: number;
      entities: RoomEntity[];
      floorItems: FloorItem[];
      wallItems: WallItem[];
    };

    const entities = new Map((p.entities ?? []).map(e => [e.instanceId, e]));
    const self = selfUserId === null
      ? null
      : [...entities.values()].find(e => e.type === 'player' && e.sourceId === selfUserId);

    useRoomStore.setState({
      currentRoom: { ...p, id: p.roomId },
      entities,
      floorItems: new Map((p.floorItems ?? []).map(f => [f.id, f])),
      wallItems: new Map((p.wallItems ?? []).map(w => [w.id, w])),
      chat: [],
      selfInstanceId: self ? self.instanceId : null,
      error: null,
    });
  });

  ws.on(Packet.ROOM_ENTER_ERROR, raw => {
    const p = raw as { message?: string };
    useRoomStore.setState({ error: p.message ?? 'Could not enter that room.' });
  });

  ws.on(Packet.ROOM_USER_ENTERED, raw => {
    const p = raw as {
      instanceId: number; userId: number; username: string;
      figureString: string; x: number; y: number; z: number; rotation: number;
    };
    useRoomStore.setState(s => {
      const next = new Map(s.entities);
      next.set(p.instanceId, {
        instanceId: p.instanceId,
        type: 'player',
        sourceId: p.userId,
        name: p.username,
        figureString: p.figureString,
        x: p.x, y: p.y, z: p.z, rotation: p.rotation,
      });
      return { entities: next };
    });
  });

  ws.on(Packet.ROOM_USER_LEFT, raw => {
    const p = raw as { instanceId: number };
    useRoomStore.setState(s => {
      if (!s.entities.has(p.instanceId)) return {};
      const next = new Map(s.entities);
      next.delete(p.instanceId);
      return { entities: next };
    });
  });

  // Movement arrives in batches: the room steps everybody one tile per tick and
  // sends the lot, so one packet can carry a dozen figures.
  ws.on(Packet.ROOM_USER_MOVED, raw => {
    const p = raw as {
      moves: Array<{ instanceId: number; x: number; y: number; z: number; rotation: number }>;
    };
    if (!p.moves?.length) return;
    useRoomStore.setState(s => {
      const next = new Map(s.entities);
      let changed = false;
      for (const m of p.moves) {
        const e = next.get(m.instanceId);
        if (!e) continue;
        next.set(m.instanceId, { ...e, x: m.x, y: m.y, z: m.z, rotation: m.rotation });
        changed = true;
      }
      return changed ? { entities: next } : {};
    });
  });

  ws.on(Packet.ROOM_USER_CHAT, raw => {
    const p = raw as { instanceId: number; message: string; colour?: number };
    pushChat({
      instanceId: p.instanceId,
      name: nameFor(p.instanceId),
      message: p.message,
      kind: 'talk',
      colour: p.colour ?? 0,
    });
  });

  ws.on(Packet.ROOM_USER_SHOUT, raw => {
    const p = raw as { instanceId: number; message: string; colour?: number };
    pushChat({
      instanceId: p.instanceId,
      name: nameFor(p.instanceId),
      message: p.message,
      kind: 'shout',
      colour: p.colour ?? 0,
    });
  });

  ws.on(Packet.ROOM_USER_WHISPER, raw => {
    const p = raw as { instanceId: number; message: string };
    pushChat({
      instanceId: p.instanceId,
      name: nameFor(p.instanceId),
      message: p.message,
      kind: 'whisper',
      colour: 0,
    });
  });

  ws.on(Packet.ROOM_DECORATION_UPDATED, raw => {
    const p = raw as Partial<RoomInfo>;
    useRoomStore.setState(s =>
      s.currentRoom ? { currentRoom: { ...s.currentRoom, ...p } } : {},
    );
  });

  ws.on(Packet.FURNI_PLACED, raw => {
    const f = raw as FloorItem;
    useRoomStore.setState(s => {
      const next = new Map(s.floorItems);
      next.set(f.id, f);
      return { floorItems: next };
    });
  });

  ws.on(Packet.FURNI_MOVED, raw => {
    const p = raw as { id: number; x: number; y: number; z: number; rotation: number };
    useRoomStore.setState(s => {
      const f = s.floorItems.get(p.id);
      if (!f) return {};
      const next = new Map(s.floorItems);
      next.set(p.id, { ...f, x: p.x, y: p.y, z: p.z, rotation: p.rotation });
      return { floorItems: next };
    });
  });

  ws.on(Packet.FURNI_ROTATED, raw => {
    const p = raw as { id: number; rotation: number };
    useRoomStore.setState(s => {
      const f = s.floorItems.get(p.id);
      if (!f) return {};
      const next = new Map(s.floorItems);
      next.set(p.id, { ...f, rotation: p.rotation });
      return { floorItems: next };
    });
  });

  ws.on(Packet.FURNI_PICKED_UP, raw => {
    const p = raw as { itemId: number };
    useRoomStore.setState(s => {
      if (!s.floorItems.has(p.itemId)) return {};
      const next = new Map(s.floorItems);
      next.delete(p.itemId);
      return { floorItems: next };
    });
  });

  ws.on(Packet.FURNI_STATE_CHANGED, raw => {
    const p = raw as { id: number; state: number };
    useRoomStore.setState(s => {
      const f = s.floorItems.get(p.id);
      if (!f) return {};
      const next = new Map(s.floorItems);
      next.set(p.id, { ...f, state: p.state });
      return { floorItems: next };
    });
  });

  ws.on(Packet.FURNI_WALL_PLACED, raw => {
    const w = raw as WallItem;
    if (w.id === undefined) return;
    useRoomStore.setState(s => {
      const next = new Map(s.wallItems);
      next.set(w.id, w);
      return { wallItems: next };
    });
  });

  ws.on(Packet.FURNI_WALL_PICKED_UP, raw => {
    const p = raw as { itemId: number };
    useRoomStore.setState(s => {
      if (!s.wallItems.has(p.itemId)) return {};
      const next = new Map(s.wallItems);
      next.delete(p.itemId);
      return { wallItems: next };
    });
  });
}
