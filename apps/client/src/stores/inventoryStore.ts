import { create } from 'zustand';
import { getWsClient } from '@/ws/WsClient';
import { Packet } from '@/protocol/packets';

export interface InventoryItem {
  id: number;
  baseId: number;
  name: string;
  type: string;
  extra: string;
}

interface InventoryStore {
  items: InventoryItem[];
  loaded: boolean;

  load: () => void;
}

export const useInventoryStore = create<InventoryStore>((_set) => ({
  items: [],
  loaded: false,

  load() {
    getWsClient().send(Packet.INV_LIST, {});
  },
}));

export function initInventoryListeners(): void {
  getWsClient().on(Packet.INV_LIST_RESULT, (raw) => {
    const p = raw as { items: InventoryItem[] };
    useInventoryStore.setState({ items: p.items, loaded: true });
  });
}
