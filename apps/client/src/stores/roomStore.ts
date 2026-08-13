import { create } from 'zustand';
import { getWsClient } from '@/ws/WsClient';
import { Packet } from '@/protocol/packets';

export interface RoomUser {
  userId: number;
  username: string;
  x: number;
  y: number;
  z: number;
  dir: number;
  figure: string;
}

export interface RoomFurni {
  id: number;
  baseId: number;
  x: number;
  y: number;
  z: number;
  dir: number;
  state: string;
}

export interface RoomInfo {
  id: number;
  name: string;
  description: string;
  ownerId: number;
  ownerName: string;
  modelId: string;
  maxUsers: number;
}

export interface RoomStore {
  currentRoom: RoomInfo | null;
  users: Map<number, RoomUser>;
  furni: Map<number, RoomFurni>;
  chat: Array<{ userId: number; username: string; message: string; ts: number }>;

  joinRoom: (roomId: number) => void;
  leaveRoom: () => void;
  move: (x: number, y: number) => void;
  chat: (message: string) => void;
}

export const useRoomStore = create<RoomStore>((set, get) => ({
  currentRoom: null,
  users: new Map(),
  furni: new Map(),
  chat: [],

  joinRoom(roomId) {
    getWsClient().send(Packet.ROOM_JOIN, { roomId });
  },

  leaveRoom() {
    getWsClient().send(Packet.ROOM_LEAVE, {});
    set({ currentRoom: null, users: new Map(), furni: new Map(), chat: [] });
  },

  move(x, y) {
    getWsClient().send(Packet.ROOM_MOVE, { x, y });
  },

  chat(message: string) {
    getWsClient().send(Packet.ROOM_CHAT, { message });
  },
}));

export function initRoomListeners(): void {
  const ws = getWsClient();

  ws.on(Packet.ROOM_STATE, (raw) => {
    const p = raw as {
      room: RoomInfo;
      users: RoomUser[];
      furni: RoomFurni[];
    };
    const users = new Map(p.users.map(u => [u.userId, u]));
    const furni = new Map(p.furni.map(f => [f.id, f]));
    useRoomStore.setState({ currentRoom: p.room, users, furni });
  });

  ws.on(Packet.ROOM_USER_JOIN, (raw) => {
    const u = raw as RoomUser;
    useRoomStore.setState((s) => {
      const next = new Map(s.users);
      next.set(u.userId, u);
      return { users: next };
    });
  });

  ws.on(Packet.ROOM_USER_LEAVE, (raw) => {
    const p = raw as { userId: number };
    useRoomStore.setState((s) => {
      const next = new Map(s.users);
      next.delete(p.userId);
      return { users: next };
    });
  });

  ws.on(Packet.ROOM_USER_MOVE, (raw) => {
    const p = raw as { userId: number; x: number; y: number; z: number; dir: number };
    useRoomStore.setState((s) => {
      const user = s.users.get(p.userId);
      if (!user) return {};
      const next = new Map(s.users);
      next.set(p.userId, { ...user, x: p.x, y: p.y, z: p.z, dir: p.dir });
      return { users: next };
    });
  });

  ws.on(Packet.ROOM_CHAT_BROADCAST, (raw) => {
    const p = raw as { userId: number; username: string; message: string };
    useRoomStore.setState((s) => ({
      chat: [...s.chat.slice(-99), { ...p, ts: Date.now() }],
    }));
  });

  ws.on(Packet.FURNI_PLACED, (raw) => {
    const f = raw as RoomFurni;
    useRoomStore.setState((s) => {
      const next = new Map(s.furni);
      next.set(f.id, f);
      return { furni: next };
    });
  });

  ws.on(Packet.FURNI_MOVED, (raw) => {
    const p = raw as { id: number; x: number; y: number; z: number; dir: number };
    useRoomStore.setState((s) => {
      const f = s.furni.get(p.id);
      if (!f) return {};
      const next = new Map(s.furni);
      next.set(p.id, { ...f, x: p.x, y: p.y, z: p.z, dir: p.dir });
      return { furni: next };
    });
  });

  ws.on(Packet.FURNI_PICKED_UP, (raw) => {
    const p = raw as { id: number };
    useRoomStore.setState((s) => {
      const next = new Map(s.furni);
      next.delete(p.id);
      return { furni: next };
    });
  });
}
