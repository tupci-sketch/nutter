-- V3: Economy — transactions, catalogue, marketplace, trades, vouchers, memberships

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Immutable transaction ledger — rows are NEVER updated or deleted
CREATE TABLE habnut_transactions (
    id                  BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    transaction_hash    CHAR(64)            NOT NULL,
    idempotency_key     CHAR(36)            NOT NULL,
    user_id             INT UNSIGNED        NOT NULL,
    type                VARCHAR(64)         NOT NULL,
    currency            ENUM('credits','diamonds','nut_points','seasonal') NOT NULL,
    amount              INT                 NOT NULL,
    balance_before      INT UNSIGNED        NOT NULL,
    balance_after       INT UNSIGNED        NOT NULL,
    description         VARCHAR(255)        NOT NULL,
    reference_id        VARCHAR(64)         NULL,
    reference_type      VARCHAR(64)         NULL,
    performed_by_id     INT UNSIGNED        NULL,
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_tx_hash (transaction_hash),
    UNIQUE KEY uq_tx_idempotency (user_id, idempotency_key),
    KEY idx_tx_user (user_id, created_at),
    KEY idx_tx_ref (reference_type, reference_id),
    CONSTRAINT fk_tx_user FOREIGN KEY (user_id) REFERENCES habnut_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── CATALOGUE ────────────────────────────────────────────────────────────────

CREATE TABLE habnut_catalogue_pages (
    id          INT UNSIGNED        NOT NULL AUTO_INCREMENT,
    parent_id   INT UNSIGNED        NULL,
    name        VARCHAR(64)         NOT NULL,
    caption     VARCHAR(64)         NOT NULL DEFAULT '',
    icon        SMALLINT UNSIGNED   NOT NULL DEFAULT 0,
    visible     TINYINT(1)          NOT NULL DEFAULT 1,
    world_id    ENUM('classic','nutropolis','both') NOT NULL DEFAULT 'both',
    layout      VARCHAR(64)         NOT NULL DEFAULT 'default_3x3',
    order_index SMALLINT UNSIGNED   NOT NULL DEFAULT 0,
    min_rank    TINYINT UNSIGNED    NOT NULL DEFAULT 1,
    enabled     TINYINT(1)          NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_cat_page_parent (parent_id, visible, order_index),
    CONSTRAINT fk_catpage_parent FOREIGN KEY (parent_id) REFERENCES habnut_catalogue_pages (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_catalogue_offers (
    id                  INT UNSIGNED        NOT NULL AUTO_INCREMENT,
    page_id             INT UNSIGNED        NOT NULL,
    name                VARCHAR(128)        NOT NULL,
    description         VARCHAR(512)        NOT NULL DEFAULT '',
    items_json          JSON                NOT NULL,
    price_credits       INT UNSIGNED        NOT NULL DEFAULT 0,
    price_diamonds      INT UNSIGNED        NOT NULL DEFAULT 0,
    price_nut_points    INT UNSIGNED        NOT NULL DEFAULT 0,
    price_seasonal      INT UNSIGNED        NOT NULL DEFAULT 0,
    is_limited          TINYINT(1)          NOT NULL DEFAULT 0,
    limited_total       INT UNSIGNED        NULL,
    limited_sold        INT UNSIGNED        NOT NULL DEFAULT 0,
    giftable            TINYINT(1)          NOT NULL DEFAULT 1,
    club_only           TINYINT(1)          NOT NULL DEFAULT 0,
    order_index         SMALLINT UNSIGNED   NOT NULL DEFAULT 0,
    enabled             TINYINT(1)          NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_catoff_page (page_id, enabled, order_index),
    CONSTRAINT fk_catoff_page FOREIGN KEY (page_id) REFERENCES habnut_catalogue_pages (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── MARKETPLACE ──────────────────────────────────────────────────────────────

CREATE TABLE habnut_marketplace_listings (
    id          BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    base_item_id INT UNSIGNED       NOT NULL,
    item_id     BIGINT UNSIGNED     NOT NULL,
    seller_id   INT UNSIGNED        NOT NULL,
    price       INT UNSIGNED        NOT NULL,
    listed_at   DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  DATETIME            NOT NULL,
    sold_at     DATETIME            NULL,
    buyer_id    INT UNSIGNED        NULL,
    cancelled_at DATETIME           NULL,
    PRIMARY KEY (id),
    KEY idx_mkt_active (base_item_id, sold_at, cancelled_at, expires_at),
    KEY idx_mkt_seller (seller_id),
    KEY idx_mkt_price (price),
    CONSTRAINT fk_mkt_seller FOREIGN KEY (seller_id) REFERENCES habnut_users (id),
    CONSTRAINT fk_mkt_base FOREIGN KEY (base_item_id) REFERENCES habnut_items_base (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_marketplace_price_history (
    id              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    base_item_id    INT UNSIGNED        NOT NULL,
    avg_price       INT UNSIGNED        NOT NULL,
    volume          INT UNSIGNED        NOT NULL DEFAULT 1,
    recorded_at     DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_mkt_ph (base_item_id, recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── TRADES ───────────────────────────────────────────────────────────────────

CREATE TABLE habnut_trades (
    id              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    user_id_a       INT UNSIGNED        NOT NULL,
    user_id_b       INT UNSIGNED        NOT NULL,
    state           ENUM('offering','confirming','completed','cancelled') NOT NULL DEFAULT 'offering',
    completed_at    DATETIME            NULL,
    cancelled_at    DATETIME            NULL,
    cancel_reason   VARCHAR(64)         NULL,
    created_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_trade_users (user_id_a, user_id_b),
    CONSTRAINT fk_trade_a FOREIGN KEY (user_id_a) REFERENCES habnut_users (id),
    CONSTRAINT fk_trade_b FOREIGN KEY (user_id_b) REFERENCES habnut_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_trade_items (
    trade_id    BIGINT UNSIGNED     NOT NULL,
    user_id     INT UNSIGNED        NOT NULL,
    item_id     BIGINT UNSIGNED     NOT NULL,
    PRIMARY KEY (trade_id, item_id),
    KEY idx_ti_user (trade_id, user_id),
    CONSTRAINT fk_ti_trade FOREIGN KEY (trade_id) REFERENCES habnut_trades (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── VOUCHERS ─────────────────────────────────────────────────────────────────

CREATE TABLE habnut_vouchers (
    id              INT UNSIGNED        NOT NULL AUTO_INCREMENT,
    code            VARCHAR(32)         NOT NULL,
    reward_type     VARCHAR(32)         NOT NULL,
    reward_json     JSON                NOT NULL,
    uses_remaining  INT                 NOT NULL DEFAULT 1,
    expires_at      DATETIME            NULL,
    created_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_voucher_code (code),
    KEY idx_voucher_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_voucher_uses (
    voucher_id  INT UNSIGNED    NOT NULL,
    user_id     INT UNSIGNED    NOT NULL,
    used_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (voucher_id, user_id),
    CONSTRAINT fk_vuse_voucher FOREIGN KEY (voucher_id) REFERENCES habnut_vouchers (id),
    CONSTRAINT fk_vuse_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── MEMBERSHIPS ──────────────────────────────────────────────────────────────

CREATE TABLE habnut_memberships (
    id          INT UNSIGNED        NOT NULL AUTO_INCREMENT,
    user_id     INT UNSIGNED        NOT NULL,
    tier        ENUM('none','bronze','silver','gold','diamond') NOT NULL,
    started_at  DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  DATETIME            NOT NULL,
    granted_by  INT UNSIGNED        NULL,
    payment_ref VARCHAR(128)        NULL,
    PRIMARY KEY (id),
    KEY idx_membership_user (user_id, expires_at),
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── GIFTS ────────────────────────────────────────────────────────────────────

CREATE TABLE habnut_gifts (
    id              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    offer_id        INT UNSIGNED        NOT NULL,
    sender_id       INT UNSIGNED        NOT NULL,
    recipient_id    INT UNSIGNED        NOT NULL,
    message         VARCHAR(255)        NOT NULL DEFAULT '',
    ribbon_id       TINYINT UNSIGNED    NOT NULL DEFAULT 0,
    colour_id       TINYINT UNSIGNED    NOT NULL DEFAULT 0,
    item_id         BIGINT UNSIGNED     NULL,
    sent_at         DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    opened_at       DATETIME            NULL,
    PRIMARY KEY (id),
    KEY idx_gift_recipient (recipient_id),
    CONSTRAINT fk_gift_sender FOREIGN KEY (sender_id) REFERENCES habnut_users (id),
    CONSTRAINT fk_gift_recipient FOREIGN KEY (recipient_id) REFERENCES habnut_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
