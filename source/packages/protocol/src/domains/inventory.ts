export const InventoryPacketTypes = {
  // c2s
  LIST: 'inventory.list',
  SEARCH: 'inventory.search',
  SORT: 'inventory.sort',
  LIST_BADGES: 'inventory.badges.list',
  EQUIP_BADGE: 'inventory.badge.equip',
  UNEQUIP_BADGE: 'inventory.badge.unequip',

  // s2c
  LIST_RESULT: 'inventory.list.result',
  SEARCH_RESULT: 'inventory.search.result',
  ITEM_ADDED: 'inventory.item.added',
  ITEM_REMOVED: 'inventory.item.removed',
  BADGES_LIST_RESULT: 'inventory.badges.list.result',
  BADGE_EQUIPPED: 'inventory.badge.equipped',
} as const;

export type InventoryPacketType = (typeof InventoryPacketTypes)[keyof typeof InventoryPacketTypes];

export interface InventoryItem {
  id: number;
  baseItemId: number;
  name: string;
  description: string;
  spriteId: string;
  isRecyclable: boolean;
  isTradeable: boolean;
  isGroupable: boolean;
  extra: string | null;
  limitedEditionNumber: number | null;
  limitedEditionTotal: number | null;
}

export interface InventoryBadge {
  id: string;
  name: string;
  description: string;
  spriteId: string;
  equipped: boolean;
  slotIndex: number | null;
  earnedAt: string;
}

export interface InventoryListPayload {
  page: number;
  pageSize: number;
  filter?: {
    type?: 'floor' | 'wall' | 'badge' | 'clothing';
    category?: string;
  };
}

export interface InventorySearchPayload {
  query: string;
  page: number;
  pageSize: number;
}

export interface InventoryListResultPayload {
  items: InventoryItem[];
  total: number;
  page: number;
  pageSize: number;
}

export interface InventoryItemAddedPayload {
  item: InventoryItem;
  source: string;
}

export interface InventoryItemRemovedPayload {
  itemId: number;
}
