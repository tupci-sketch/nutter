-- V8: Assets, pets, bots, events, photos, sound, staff prefs, feature flags, system settings

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ─── ASSET REGISTRY ───────────────────────────────────────────────────────────

CREATE TABLE habnut_asset_registry (
    id                  BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    sprite_id           VARCHAR(128)        NOT NULL,
    type                ENUM('floor_furni','wall_furni','avatar','effect','badge','pet','background','icon') NOT NULL,
    filename            VARCHAR(255)        NOT NULL,
    hash_sha256         CHAR(64)            NOT NULL,
    size_bytes          BIGINT UNSIGNED     NOT NULL DEFAULT 0,
    nitro_converted     TINYINT(1)          NOT NULL DEFAULT 0,
    validated_at        DATETIME            NULL,
    created_at          DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_asset_sprite (sprite_id, type),
    KEY idx_asset_type (type, nitro_converted),
    KEY idx_asset_hash (hash_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── PETS ─────────────────────────────────────────────────────────────────────

CREATE TABLE habnut_pets (
    id              INT UNSIGNED        NOT NULL AUTO_INCREMENT,
    type            VARCHAR(32)         NOT NULL,
    name            VARCHAR(32)         NOT NULL,
    owner_user_id   INT UNSIGNED        NOT NULL,
    genetics_json   JSON                NOT NULL DEFAULT (JSON_OBJECT()),
    stats_json      JSON                NOT NULL DEFAULT (JSON_OBJECT()),
    equipment_ids   JSON                NOT NULL DEFAULT (JSON_ARRAY()),
    rarity          ENUM('common','uncommon','rare','legendary') NOT NULL DEFAULT 'common',
    created_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_pet_owner (owner_user_id),
    CONSTRAINT fk_pet_owner FOREIGN KEY (owner_user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_pet_commands (
    pet_id      INT UNSIGNED    NOT NULL,
    command     VARCHAR(32)     NOT NULL,
    trained_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (pet_id, command),
    CONSTRAINT fk_pcmd_pet FOREIGN KEY (pet_id) REFERENCES habnut_pets (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_pet_placements (
    id          BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    pet_id      INT UNSIGNED        NOT NULL,
    room_id     INT UNSIGNED        NOT NULL,
    x           SMALLINT            NOT NULL,
    y           SMALLINT            NOT NULL,
    direction   TINYINT UNSIGNED    NOT NULL DEFAULT 2,
    placed_at   DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    picked_up_at DATETIME           NULL,
    PRIMARY KEY (id),
    KEY idx_pet_placement_room (room_id, picked_up_at),
    CONSTRAINT fk_pplace_pet FOREIGN KEY (pet_id) REFERENCES habnut_pets (id) ON DELETE CASCADE,
    CONSTRAINT fk_pplace_room FOREIGN KEY (room_id) REFERENCES habnut_rooms (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── BOTS ─────────────────────────────────────────────────────────────────────

CREATE TABLE habnut_bots (
    id              INT UNSIGNED        NOT NULL AUTO_INCREMENT,
    name            VARCHAR(25)         NOT NULL,
    motto           VARCHAR(190)        NOT NULL DEFAULT '',
    figure_string   VARCHAR(512)        NOT NULL DEFAULT '',
    gender          CHAR(1)             NOT NULL DEFAULT 'M',
    owner_user_id   INT UNSIGNED        NOT NULL,
    chat_mode       ENUM('disabled','random','on_walk_on','reaction') NOT NULL DEFAULT 'disabled',
    walk_mode       ENUM('stand','random_walk','walk_to_avatar','path') NOT NULL DEFAULT 'stand',
    chat_lines_json JSON                NOT NULL DEFAULT (JSON_ARRAY()),
    chat_delay_ms   INT UNSIGNED        NOT NULL DEFAULT 5000,
    created_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_bot_owner (owner_user_id),
    CONSTRAINT fk_bot_owner FOREIGN KEY (owner_user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_bot_placements (
    id          BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    bot_id      INT UNSIGNED        NOT NULL,
    room_id     INT UNSIGNED        NOT NULL,
    x           SMALLINT            NOT NULL,
    y           SMALLINT            NOT NULL,
    direction   TINYINT UNSIGNED    NOT NULL DEFAULT 2,
    placed_at   DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    picked_up_at DATETIME           NULL,
    PRIMARY KEY (id),
    KEY idx_bot_place_room (room_id, picked_up_at),
    CONSTRAINT fk_bplace_bot FOREIGN KEY (bot_id) REFERENCES habnut_bots (id) ON DELETE CASCADE,
    CONSTRAINT fk_bplace_room FOREIGN KEY (room_id) REFERENCES habnut_rooms (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── EVENTS ───────────────────────────────────────────────────────────────────

CREATE TABLE habnut_events (
    id                  INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    name                VARCHAR(128)    NOT NULL,
    description         TEXT            NOT NULL DEFAULT '',
    type                VARCHAR(32)     NOT NULL DEFAULT 'social',
    starts_at           DATETIME        NOT NULL,
    ends_at             DATETIME        NOT NULL,
    room_id             INT UNSIGNED    NULL,
    host_user_id        INT UNSIGNED    NOT NULL,
    max_participants    SMALLINT UNSIGNED NULL,
    status              ENUM('scheduled','active','ended','cancelled') NOT NULL DEFAULT 'scheduled',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_event_status (status, starts_at),
    KEY idx_event_host (host_user_id),
    CONSTRAINT fk_event_host FOREIGN KEY (host_user_id) REFERENCES habnut_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_event_participants (
    event_id        INT UNSIGNED    NOT NULL,
    user_id         INT UNSIGNED    NOT NULL,
    registered_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, user_id),
    CONSTRAINT fk_ep_event FOREIGN KEY (event_id) REFERENCES habnut_events (id) ON DELETE CASCADE,
    CONSTRAINT fk_ep_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── COMPETITIONS ─────────────────────────────────────────────────────────────

CREATE TABLE habnut_competitions (
    id          INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    name        VARCHAR(128)    NOT NULL,
    description TEXT            NOT NULL DEFAULT '',
    game_type   VARCHAR(32)     NOT NULL,
    format      VARCHAR(32)     NOT NULL DEFAULT 'bracket',
    status      ENUM('upcoming','active','finished','cancelled') NOT NULL DEFAULT 'upcoming',
    starts_at   DATETIME        NOT NULL,
    ends_at     DATETIME        NOT NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_comp_status (status, starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── CAMERA / PHOTOS ──────────────────────────────────────────────────────────

CREATE TABLE habnut_photos (
    id              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    taker_user_id   INT UNSIGNED        NOT NULL,
    room_id         INT UNSIGNED        NOT NULL,
    data_url        VARCHAR(512)        NOT NULL,
    caption         VARCHAR(255)        NOT NULL DEFAULT '',
    purchased       TINYINT(1)          NOT NULL DEFAULT 0,
    created_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_photo_user (taker_user_id),
    KEY idx_photo_room (room_id),
    CONSTRAINT fk_photo_user FOREIGN KEY (taker_user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── SOUND ────────────────────────────────────────────────────────────────────

CREATE TABLE habnut_sound_tracks (
    id          INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    name        VARCHAR(128)    NOT NULL,
    artist      VARCHAR(64)     NOT NULL DEFAULT '',
    duration_ms INT UNSIGNED    NOT NULL DEFAULT 0,
    file_url    VARCHAR(512)    NOT NULL,
    enabled     TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_track_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_room_playlists (
    room_id     INT UNSIGNED    NOT NULL,
    track_id    INT UNSIGNED    NOT NULL,
    position    TINYINT UNSIGNED NOT NULL DEFAULT 0,
    added_by_id INT UNSIGNED    NOT NULL,
    added_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (room_id, track_id),
    KEY idx_playlist_pos (room_id, position),
    CONSTRAINT fk_playlist_room FOREIGN KEY (room_id) REFERENCES habnut_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_playlist_track FOREIGN KEY (track_id) REFERENCES habnut_sound_tracks (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── STAFF WIDGET PREFERENCES ─────────────────────────────────────────────────

CREATE TABLE habnut_staff_widget_prefs (
    user_id     INT UNSIGNED    NOT NULL,
    layout_json JSON            NOT NULL DEFAULT (JSON_ARRAY()),
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_swp_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── FEATURE FLAGS ────────────────────────────────────────────────────────────

CREATE TABLE habnut_feature_flags (
    name            VARCHAR(64)     NOT NULL,
    value           VARCHAR(255)    NOT NULL DEFAULT 'false',
    description     VARCHAR(255)    NOT NULL DEFAULT '',
    updated_by_id   INT UNSIGNED    NULL,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO habnut_feature_flags (name, value, description) VALUES
    ('registration_open',       'true',  'Allow new user registration'),
    ('marketplace_enabled',     'true',  'Enable marketplace feature'),
    ('trading_enabled',         'true',  'Enable player-to-player trading'),
    ('garden_enabled',          'true',  'Enable community garden'),
    ('nutropolis_enabled',      'true',  'Enable Nutropolis RP world'),
    ('wired_global_signals',    'true',  'Allow cross-room wired signals'),
    ('camera_enabled',          'true',  'Enable in-room camera feature'),
    ('sound_enabled',           'true',  'Enable room sound machine'),
    ('seasonal_enabled',        'true',  'Enable seasonal content and currency'),
    ('maintenance_mode',        'false', 'Disable login during maintenance');

-- ─── SYSTEM SETTINGS ──────────────────────────────────────────────────────────

CREATE TABLE habnut_system_settings (
    setting_key     VARCHAR(64)     NOT NULL,
    setting_value   TEXT            NOT NULL,
    description     VARCHAR(255)    NOT NULL DEFAULT '',
    updated_by_id   INT UNSIGNED    NULL,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO habnut_system_settings (setting_key, setting_value, description) VALUES
    ('hotel_name',          'Habnut',       'Display name of the hotel'),
    ('hotel_slogan',        'Your world. Your rules.', 'Hotel slogan'),
    ('max_room_furni',      '2000',         'Default maximum furniture per room'),
    ('max_user_rooms',      '25',           'Default maximum rooms per player'),
    ('max_friends',         '200',          'Maximum friends per player'),
    ('max_groups',          '50',           'Maximum groups per player'),
    ('chat_filter_enabled', 'true',         'Whether word filter is active'),
    ('registration_bonus_credits',  '50',   'Credits granted on registration'),
    ('registration_bonus_diamonds', '0',    'Diamonds granted on registration'),
    ('trade_cooldown_ms',   '30000',        'Milliseconds between trades');
