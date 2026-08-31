export const CataloguePacketTypes = {
  // c2s
  PAGES: 'catalogue.pages',
  PAGE: 'catalogue.page',
  OFFERS: 'catalogue.offers',
  PURCHASE: 'catalogue.purchase',
  GIFT: 'catalogue.gift',
  REDEEM_VOUCHER: 'catalogue.voucher.redeem',
  CLUB_INFO: 'catalogue.club.info',
  PRESENT_OPEN: 'catalogue.present.open',
  RECYCLER_INFO: 'catalogue.recycler.info',
  RECYCLE: 'catalogue.recycle',

  // s2c
  PAGES_RESULT: 'catalogue.pages.result',
  PAGE_RESULT: 'catalogue.page.result',
  PURCHASE_SUCCESS: 'catalogue.purchase.success',
  PURCHASE_ERROR: 'catalogue.purchase.error',
  GIFT_SENT: 'catalogue.gift.sent',
  VOUCHER_REDEEMED: 'catalogue.voucher.redeemed',
  VOUCHER_ERROR: 'catalogue.voucher.error',
  CLUB_INFO_RESULT: 'catalogue.club.info.result',
  PRESENT_OPENED: 'catalogue.present.opened',
  RECYCLER_INFO_RESULT: 'catalogue.recycler.info.result',
  RECYCLED: 'catalogue.recycled',
} as const;

export type CataloguePacketType = (typeof CataloguePacketTypes)[keyof typeof CataloguePacketTypes];

export type CatCurrency = 'credits' | 'diamonds' | 'nut_points' | 'seasonal';

export interface CatPage {
  id: number;
  parentId: number | null;
  name: string;
  caption: string;
  icon: number;
  visible: boolean;
  order: number;
  layout: string;
  children: CatPage[];
}

export interface CatOffer {
  id: number;
  name: string;
  description: string;
  items: Array<{
    baseItemId: number;
    count: number;
    extra: string | null;
  }>;
  priceCredits: number;
  priceDiamonds: number;
  priceNutPoints: number;
  priceSeasonal: number;
  isLimited: boolean;
  limitedTotal: number | null;
  limitedRemaining: number | null;
  giftable: boolean;
  order: number;
}

export interface CatPageResult {
  pageId: number;
  layout: string;
  offers: CatOffer[];
  teaser: string[];
}

export interface CatPurchasePayload {
  offerId: number;
  amount: number;
  idempotencyKey: string;
  extraParam?: string;
}

export interface CatGiftPayload {
  offerId: number;
  recipientUsername: string;
  message: string;
  ribbonId: number;
  colourId: number;
  idempotencyKey: string;
}

export interface CatPurchaseSuccessPayload {
  offerId: number;
  itemsAdded: Array<{ baseItemId: number; count: number }>;
  creditsRemaining: number;
  diamondsRemaining: number;
  nutPointsRemaining: number;
}

export interface CatVoucherRedeemPayload {
  code: string;
  idempotencyKey: string;
}

export interface CatVoucherRedeemedPayload {
  reward: string;
  creditsGranted: number;
  diamondsGranted: number;
  nutPointsGranted: number;
  itemsGranted: Array<{ baseItemId: number; count: number }>;
}
