export const MarketplacePacketTypes = {
  // c2s
  SEARCH: 'marketplace.search',
  LISTING_CREATE: 'marketplace.listing.create',
  LISTING_CANCEL: 'marketplace.listing.cancel',
  LISTING_BUY: 'marketplace.listing.buy',
  MY_LISTINGS: 'marketplace.listings.mine',
  PRICE_HISTORY: 'marketplace.price.history',

  // s2c
  SEARCH_RESULT: 'marketplace.search.result',
  LISTING_CREATED: 'marketplace.listing.created',
  LISTING_CANCELLED: 'marketplace.listing.cancelled',
  LISTING_SOLD: 'marketplace.listing.sold',
  LISTING_BOUGHT: 'marketplace.listing.bought',
  LISTING_EXPIRED: 'marketplace.listing.expired',
  MY_LISTINGS_RESULT: 'marketplace.listings.mine.result',
  PRICE_HISTORY_RESULT: 'marketplace.price.history.result',
  ERROR: 'marketplace.error',
} as const;

export type MarketplacePacketType = (typeof MarketplacePacketTypes)[keyof typeof MarketplacePacketTypes];

export interface MarketplaceListing {
  listingId: number;
  baseItemId: number;
  itemName: string;
  spriteId: string;
  price: number;
  sellerId: number;
  sellerName: string;
  listedAt: string;
  expiresAt: string;
  limitedEditionNumber: number | null;
  limitedEditionTotal: number | null;
}

export interface MarketplaceSearchPayload {
  query?: string;
  baseItemId?: number;
  minPrice?: number;
  maxPrice?: number;
  sortBy?: 'price_asc' | 'price_desc' | 'newest' | 'oldest';
  page: number;
  pageSize: number;
}

export interface MarketplaceListingCreatePayload {
  itemId: number;
  price: number;
  idempotencyKey: string;
}

export interface MarketplaceListingBuyPayload {
  listingId: number;
  idempotencyKey: string;
}

export interface MarketplaceSearchResultPayload {
  listings: MarketplaceListing[];
  total: number;
  page: number;
  pageSize: number;
}

export interface MarketplaceListingCreatedPayload {
  listing: MarketplaceListing;
}

export interface MarketplaceListingBoughtPayload {
  listingId: number;
  item: { baseItemId: number; name: string };
  pricePaid: number;
  creditsRemaining: number;
}

export interface MarketplacePriceHistoryPayload {
  baseItemId: number;
  period: 'day' | 'week' | 'month';
}

export interface MarketplacePriceHistoryResultPayload {
  baseItemId: number;
  dataPoints: Array<{
    timestamp: string;
    avgPrice: number;
    volume: number;
  }>;
}
