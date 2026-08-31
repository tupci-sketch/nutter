export const TradePacketTypes = {
  // c2s
  OPEN: 'trade.open',
  OFFER_ADD: 'trade.offer.add',
  OFFER_REMOVE: 'trade.offer.remove',
  CONFIRM: 'trade.confirm',
  ACCEPT: 'trade.accept',
  UNACCEPT: 'trade.unaccept',
  CANCEL: 'trade.cancel',

  // s2c
  OPENED: 'trade.opened',
  OFFER_UPDATED: 'trade.offer.updated',
  CONFIRMED: 'trade.confirmed',
  ACCEPTED: 'trade.accepted',
  UNACCEPTED: 'trade.unaccepted',
  COMPLETED: 'trade.completed',
  CANCELLED: 'trade.cancelled',
  ERROR: 'trade.error',
} as const;

export type TradePacketType = (typeof TradePacketTypes)[keyof typeof TradePacketTypes];

export interface TradeItem {
  id: number;
  baseItemId: number;
  name: string;
  spriteId: string;
}

export interface TradeOpenPayload {
  targetUserId: number;
}

export interface TradeOfferAddPayload {
  itemId: number;
}

export interface TradeOfferRemovePayload {
  itemId: number;
}

export interface TradeOpenedPayload {
  tradeId: string;
  partnerId: number;
  partnerName: string;
}

export interface TradeOfferUpdatedPayload {
  myOffer: TradeItem[];
  partnerOffer: TradeItem[];
  myAccepted: boolean;
  partnerAccepted: boolean;
}

export interface TradeCompletedPayload {
  received: TradeItem[];
  given: TradeItem[];
}

export interface TradeCancelledPayload {
  reason: 'declined' | 'disconnected' | 'partner_left';
  cancelledBy: 'self' | 'partner';
}

export interface TradeErrorPayload {
  code: string;
  reason: string;
}
