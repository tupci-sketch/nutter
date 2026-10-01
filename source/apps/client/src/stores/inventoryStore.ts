import { create } from 'zustand';
import { getWsClient } from '@/ws/WsClient';
import { Packet } from '@/protocol/packets';

export interface InventoryItem {
  id: number;
  baseId: number;
  spriteId: string;
  name: string;
  type: string;
}

interface InventoryStore {
  items: InventoryItem[];
  total: number;
  page: number;
  loaded: boolean;

  load: (page?: number) => void;
}

export const useInventoryStore = create<InventoryStore>(() => ({
  items: [],
  total: 0,
  page: 0,
  loaded: false,

  load(page = 0) {
    getWsClient().send(Packet.INVENTORY_LIST, { page, limit: 50 });
  },
}));

export function initInventoryListeners(): void {
  const ws = getWsClient();

  ws.on(Packet.INVENTORY_LIST_RESULT, raw => {
    const p = raw as { items: InventoryItem[]; total: number; page: number };
    useInventoryStore.setState({
      items: p.items ?? [],
      total: p.total ?? 0,
      page: p.page ?? 0,
      loaded: true,
    });
  });

  // An item arriving — bought, traded for, or given — appears without asking.
  ws.on(Packet.INVENTORY_ITEM_ADDED, raw => {
    const item = raw as InventoryItem;
    useInventoryStore.setState(s =>
      s.items.some(i => i.id === item.id)
        ? {}
        : { items: [item, ...s.items], total: s.total + 1 },
    );
  });

  ws.on(Packet.INVENTORY_ITEM_REMOVED, raw => {
    const p = raw as { id: number };
    useInventoryStore.setState(s => ({
      items: s.items.filter(i => i.id !== p.id),
      total: Math.max(0, s.total - 1),
    }));
  });
}
