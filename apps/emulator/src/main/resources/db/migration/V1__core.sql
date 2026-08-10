-- V1: Core tables — users, rooms, furniture, friends, social, groups, badges, achievements, quests

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ─── RANKS ────────────────────────────────────────────────────────────────────

CREATE TABLE habnut_ranks (
    id          TINYINT UNSIGNED    NOT NULL,
    name        VARCHAR(64)         NOT NULL,
    badge_id    VARCHAR(32)         NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO habnut_ranks (id, name, badge_id) VALUES
    (1, 'Player',           NULL),
    (2, 'VIP',              'ACE_VIP'),
    (3, 'Helper',           'ACE_HLP'),
    (4, 'Moderator',        'ACE_MOD'),
    (5, 'Super Moderator',  'ACE_SMD'),
    (6, 'Administrator',    'ACE_ADM'),
    (7, 'Owner',            'ACE_OWN');

-- ─── USERS ────────────────────────────────────────────────────────────────────

CREATE TABLE habnut_users (
    id                      INT UNSIGNED        NOT NULL AUTO_INCREMENT,
    username                VARCHAR(25)         NOT NULL,
    email                   VARCHAR(254)        NOT NULL,
    password_hash           VARCHAR(255)        NOT NULL,
    figure_string           VARCHAR(512)        NOT NULL    DEFAULT '',
    gender                  CHAR(1)             NOT NULL    DEFAULT 'M',
    motto                   VARCHAR(190)        NOT NULL    DEFAULT '',
    rank                    TINYINT UNSIGNED    NOT NULL    DEFAULT 1,
    email_verified          TINYINT(1)          NOT NULL    DEFAULT 0,
    two_fa_secret           VARCHAR(64)         NULL,
    two_fa_enabled          TINYINT(1)          NOT NULL    DEFAULT 0,
    credits                 INT UNSIGNED        NOT NULL    DEFAULT 0,
    diamonds                INT UNSIGNED        NOT NULL    DEFAULT 0,
    nut_points              INT UNSIGNED        NOT NULL    DEFAULT 0,
    seasonal                INT UNSIGNED        NOT NULL    DEFAULT 0,
    achievement_score       INT UNSIGNED        NOT NULL    DEFAULT 0,
    membership_tier         ENUM('none','bronze','silver','gold','diamond') NOT NULL DEFAULT 'none',
    membership_expiry       DATETIME            NULL,
    last_activity           DATETIME            NULL,
    created_at              DATETIME            NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME            NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_username  (username),
    UNIQUE KEY uq_email     (email),
    KEY idx_rank            (rank),
    KEY idx_last_activity   (last_activity),
    CONSTRAINT fk_users_rank FOREIGN KEY (rank) REFERENCES habnut_ranks (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_machine_ids (
    id              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    user_id         INT UNSIGNED        NOT NULL,
    machine_id_hash VARCHAR(64)         NOT NULL,
    first_seen      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen       DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_machine (user_id, machine_id_hash),
    KEY idx_machine_hash (machine_id_hash),
    CONSTRAINT fk_machine_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_user_settings (
    user_id         INT UNSIGNED        NOT NULL,
    setting_key     VARCHAR(64)         NOT NULL,
    setting_value   TEXT                NOT NULL,
    updated_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, setting_key),
    CONSTRAINT fk_usettings_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_notifications (
    id          BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    user_id     INT UNSIGNED        NOT NULL,
    type        VARCHAR(32)         NOT NULL,
    title       VARCHAR(128)        NOT NULL,
    body        TEXT                NOT NULL,
    link        VARCHAR(512)        NULL,
    read_at     DATETIME            NULL,
    created_at  DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_notif_user (user_id, read_at),
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── ROOM MODELS ──────────────────────────────────────────────────────────────

CREATE TABLE habnut_room_models (
    id              VARCHAR(64)     NOT NULL,
    map_data        MEDIUMTEXT      NOT NULL,
    door_x          SMALLINT        NOT NULL,
    door_y          SMALLINT        NOT NULL,
    door_direction  TINYINT         NOT NULL DEFAULT 2,
    pool_map        MEDIUMTEXT      NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_room_categories (
    id          SMALLINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64)         NOT NULL,
    parent_id   SMALLINT UNSIGNED   NULL,
    order_index SMALLINT UNSIGNED   NOT NULL DEFAULT 0,
    world_id    ENUM('classic','nutropolis') NOT NULL DEFAULT 'classic',
    icon        SMALLINT UNSIGNED   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_cat_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO habnut_room_categories (id, name, parent_id, order_index, world_id, icon) VALUES
    (1, 'Hotel', NULL, 0, 'classic', 0),
    (2, 'Games', NULL, 1, 'classic', 1),
    (3, 'Trades', NULL, 2, 'classic', 2),
    (4, 'Fan Sites', NULL, 3, 'classic', 3),
    (5, 'Groups', NULL, 4, 'classic', 4),
    (6, 'Nutropolis', NULL, 0, 'nutropolis', 5);

-- ─── ROOMS ────────────────────────────────────────────────────────────────────

CREATE TABLE habnut_rooms (
    id                  INT UNSIGNED        NOT NULL AUTO_INCREMENT,
    name                VARCHAR(60)         NOT NULL,
    description         VARCHAR(512)        NOT NULL DEFAULT '',
    owner_id            INT UNSIGNED        NOT NULL,
    model_id            VARCHAR(64)         NOT NULL,
    category_id         SMALLINT UNSIGNED   NOT NULL DEFAULT 1,
    world_id            ENUM('classic','nutropolis') NOT NULL DEFAULT 'classic',
    access_mode         TINYINT UNSIGNED    NOT NULL DEFAULT 0,
    password_hash       VARCHAR(255)        NULL,
    trade_mode          TINYINT UNSIGNED    NOT NULL DEFAULT 0,
    max_users           TINYINT UNSIGNED    NOT NULL DEFAULT 25,
    rating              INT UNSIGNED        NOT NULL DEFAULT 0,
    tags                JSON                NOT NULL DEFAULT (JSON_ARRAY()),
    allow_pets          TINYINT(1)          NOT NULL DEFAULT 1,
    allow_pets_eat      TINYINT(1)          NOT NULL DEFAULT 0,
    block_room_walk     TINYINT(1)          NOT NULL DEFAULT 0,
    allow_walkthrough   TINYINT(1)          NOT NULL DEFAULT 0,
    thumbnail_url       VARCHAR(512)        NULL,
    group_id            INT UNSIGNED        NULL,
    is_public           TINYINT(1)          NOT NULL DEFAULT 0,
    user_count          SMALLINT UNSIGNED   NOT NULL DEFAULT 0,
    visit_count         INT UNSIGNED        NOT NULL DEFAULT 0,
    created_at          DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_rooms_owner (owner_id),
    KEY idx_rooms_world (world_id, category_id),
    KEY idx_rooms_public (is_public, world_id, rating),
    KEY idx_rooms_user_count (user_count),
    CONSTRAINT fk_rooms_owner FOREIGN KEY (owner_id) REFERENCES habnut_users (id),
    CONSTRAINT fk_rooms_model FOREIGN KEY (model_id) REFERENCES habnut_room_models (id),
    CONSTRAINT fk_rooms_category FOREIGN KEY (category_id) REFERENCES habnut_room_categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_room_rights (
    room_id     INT UNSIGNED    NOT NULL,
    user_id     INT UNSIGNED    NOT NULL,
    granted_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (room_id, user_id),
    CONSTRAINT fk_rights_room FOREIGN KEY (room_id) REFERENCES habnut_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_rights_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_room_bans (
    id          INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    room_id     INT UNSIGNED    NOT NULL,
    user_id     INT UNSIGNED    NOT NULL,
    banner_id   INT UNSIGNED    NOT NULL,
    reason      VARCHAR(255)    NOT NULL DEFAULT '',
    expires_at  DATETIME        NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_room_ban (room_id, user_id),
    KEY idx_roomban_expires (expires_at),
    CONSTRAINT fk_roomban_room FOREIGN KEY (room_id) REFERENCES habnut_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_roomban_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_room_ratings (
    room_id     INT UNSIGNED    NOT NULL,
    user_id     INT UNSIGNED    NOT NULL,
    rated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (room_id, user_id),
    CONSTRAINT fk_rating_room FOREIGN KEY (room_id) REFERENCES habnut_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_rating_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_room_favorites (
    room_id     INT UNSIGNED    NOT NULL,
    user_id     INT UNSIGNED    NOT NULL,
    added_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, room_id),
    CONSTRAINT fk_fav_room FOREIGN KEY (room_id) REFERENCES habnut_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_fav_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── ITEMS ────────────────────────────────────────────────────────────────────

CREATE TABLE habnut_items_base (
    id                  INT UNSIGNED        NOT NULL AUTO_INCREMENT,
    sprite_id           VARCHAR(128)        NOT NULL,
    name                VARCHAR(128)        NOT NULL,
    description         VARCHAR(512)        NOT NULL DEFAULT '',
    type                ENUM('floor','wall','clothing','effect','badge','pet') NOT NULL DEFAULT 'floor',
    width               TINYINT UNSIGNED    NOT NULL DEFAULT 1,
    length              TINYINT UNSIGNED    NOT NULL DEFAULT 1,
    stack_height        DECIMAL(6,3)        NOT NULL DEFAULT 1.000,
    can_stack           TINYINT(1)          NOT NULL DEFAULT 0,
    can_sit             TINYINT(1)          NOT NULL DEFAULT 0,
    is_walkable         TINYINT(1)          NOT NULL DEFAULT 0,
    is_tradeable        TINYINT(1)          NOT NULL DEFAULT 1,
    is_recyclable       TINYINT(1)          NOT NULL DEFAULT 1,
    is_groupable        TINYINT(1)          NOT NULL DEFAULT 0,
    interaction_type    VARCHAR(64)         NOT NULL DEFAULT 'default',
    interaction_modes   TINYINT UNSIGNED    NOT NULL DEFAULT 1,
    furni_line_id       VARCHAR(64)         NULL,
    environment         VARCHAR(64)         NULL,
    PRIMARY KEY (id),
    KEY idx_items_base_sprite (sprite_id),
    KEY idx_items_base_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_items_inventory (
    id                      BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    base_item_id            INT UNSIGNED        NOT NULL,
    user_id                 INT UNSIGNED        NOT NULL,
    type                    ENUM('floor','wall') NOT NULL DEFAULT 'floor',
    extra                   VARCHAR(255)        NULL,
    limited_edition_number  INT UNSIGNED        NULL,
    limited_edition_total   INT UNSIGNED        NULL,
    acquired_at             DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_inv_user (user_id),
    KEY idx_inv_base (base_item_id),
    CONSTRAINT fk_inv_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_inv_base FOREIGN KEY (base_item_id) REFERENCES habnut_items_base (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_floor_items (
    id          BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    base_item_id INT UNSIGNED       NOT NULL,
    room_id     INT UNSIGNED        NOT NULL,
    user_id     INT UNSIGNED        NOT NULL,
    x           SMALLINT            NOT NULL,
    y           SMALLINT            NOT NULL,
    z           DECIMAL(8,3)        NOT NULL DEFAULT 0.000,
    rotation    TINYINT UNSIGNED    NOT NULL DEFAULT 0,
    state       VARCHAR(64)         NOT NULL DEFAULT '0',
    extra       VARCHAR(255)        NULL,
    group_id    INT UNSIGNED        NULL,
    placed_at   DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_floor_room (room_id),
    KEY idx_floor_user (user_id),
    CONSTRAINT fk_floor_room FOREIGN KEY (room_id) REFERENCES habnut_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_floor_user FOREIGN KEY (user_id) REFERENCES habnut_users (id),
    CONSTRAINT fk_floor_base FOREIGN KEY (base_item_id) REFERENCES habnut_items_base (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_wall_items (
    id          BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    base_item_id INT UNSIGNED       NOT NULL,
    room_id     INT UNSIGNED        NOT NULL,
    user_id     INT UNSIGNED        NOT NULL,
    wall_x      SMALLINT            NOT NULL,
    wall_y      SMALLINT            NOT NULL,
    wall_loc    CHAR(1)             NOT NULL DEFAULT 'l',
    sx          SMALLINT            NOT NULL DEFAULT 0,
    sy          SMALLINT            NOT NULL DEFAULT 0,
    state       VARCHAR(64)         NOT NULL DEFAULT '0',
    extra       VARCHAR(255)        NULL,
    group_id    INT UNSIGNED        NULL,
    placed_at   DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_wall_room (room_id),
    KEY idx_wall_user (user_id),
    CONSTRAINT fk_wall_room FOREIGN KEY (room_id) REFERENCES habnut_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_wall_user FOREIGN KEY (user_id) REFERENCES habnut_users (id),
    CONSTRAINT fk_wall_base FOREIGN KEY (base_item_id) REFERENCES habnut_items_base (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_teleport_links (
    furni_id_a  BIGINT UNSIGNED     NOT NULL,
    furni_id_b  BIGINT UNSIGNED     NOT NULL,
    room_id_a   INT UNSIGNED        NOT NULL,
    room_id_b   INT UNSIGNED        NOT NULL,
    PRIMARY KEY (furni_id_a),
    KEY idx_tp_b (furni_id_b)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── FRIENDS & SOCIAL ─────────────────────────────────────────────────────────

CREATE TABLE habnut_friends (
    user_id_a   INT UNSIGNED    NOT NULL,
    user_id_b   INT UNSIGNED    NOT NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id_a, user_id_b),
    KEY idx_friends_b (user_id_b),
    CONSTRAINT fk_friends_a FOREIGN KEY (user_id_a) REFERENCES habnut_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_friends_b FOREIGN KEY (user_id_b) REFERENCES habnut_users (id) ON DELETE CASCADE,
    CONSTRAINT ck_friends_order CHECK (user_id_a < user_id_b)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_friend_requests (
    from_user_id    INT UNSIGNED    NOT NULL,
    to_user_id      INT UNSIGNED    NOT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (from_user_id, to_user_id),
    KEY idx_freq_to (to_user_id),
    CONSTRAINT fk_freq_from FOREIGN KEY (from_user_id) REFERENCES habnut_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_freq_to FOREIGN KEY (to_user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_blocks (
    blocker_id  INT UNSIGNED    NOT NULL,
    blocked_id  INT UNSIGNED    NOT NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (blocker_id, blocked_id),
    KEY idx_blocks_blocked (blocked_id),
    CONSTRAINT fk_block_blocker FOREIGN KEY (blocker_id) REFERENCES habnut_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_block_blocked FOREIGN KEY (blocked_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_user_relations (
    user_id         INT UNSIGNED    NOT NULL,
    target_id       INT UNSIGNED    NOT NULL,
    relation_type   TINYINT UNSIGNED NOT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, target_id),
    CONSTRAINT fk_rel_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_rel_target FOREIGN KEY (target_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_messages (
    id                  BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    from_user_id        INT UNSIGNED        NOT NULL,
    to_user_id          INT UNSIGNED        NOT NULL,
    body                TEXT                NOT NULL,
    sent_at             DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at             DATETIME            NULL,
    deleted_by_sender   TINYINT(1)          NOT NULL DEFAULT 0,
    deleted_by_receiver TINYINT(1)          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_msg_to (to_user_id, read_at),
    KEY idx_msg_from (from_user_id),
    CONSTRAINT fk_msg_from FOREIGN KEY (from_user_id) REFERENCES habnut_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_to FOREIGN KEY (to_user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── GROUPS ───────────────────────────────────────────────────────────────────

CREATE TABLE habnut_groups (
    id          INT UNSIGNED        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64)         NOT NULL,
    description VARCHAR(512)        NOT NULL DEFAULT '',
    badge       VARCHAR(255)        NOT NULL DEFAULT '',
    owner_id    INT UNSIGNED        NOT NULL,
    room_id     INT UNSIGNED        NULL,
    access_mode ENUM('open','request','invite_only') NOT NULL DEFAULT 'open',
    forum_mode  ENUM('open','members_only','admins_only','disabled') NOT NULL DEFAULT 'open',
    member_count INT UNSIGNED       NOT NULL DEFAULT 1,
    created_at  DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_group_owner (owner_id),
    KEY idx_group_room (room_id),
    FULLTEXT KEY ft_group_name (name),
    CONSTRAINT fk_group_owner FOREIGN KEY (owner_id) REFERENCES habnut_users (id),
    CONSTRAINT fk_group_room FOREIGN KEY (room_id) REFERENCES habnut_rooms (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_group_members (
    group_id    INT UNSIGNED        NOT NULL,
    user_id     INT UNSIGNED        NOT NULL,
    rank        ENUM('owner','admin','member','requested','invited') NOT NULL DEFAULT 'member',
    joined_at   DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, user_id),
    KEY idx_gm_user (user_id),
    KEY idx_gm_rank (group_id, rank),
    CONSTRAINT fk_gm_group FOREIGN KEY (group_id) REFERENCES habnut_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_gm_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_group_forum_threads (
    id              INT UNSIGNED        NOT NULL AUTO_INCREMENT,
    group_id        INT UNSIGNED        NOT NULL,
    author_id       INT UNSIGNED        NOT NULL,
    subject         VARCHAR(128)        NOT NULL,
    reply_count     INT UNSIGNED        NOT NULL DEFAULT 0,
    pinned          TINYINT(1)          NOT NULL DEFAULT 0,
    locked          TINYINT(1)          NOT NULL DEFAULT 0,
    hidden          TINYINT(1)          NOT NULL DEFAULT 0,
    created_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_reply_at   DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_gft_group (group_id, hidden, pinned, last_reply_at),
    CONSTRAINT fk_gft_group FOREIGN KEY (group_id) REFERENCES habnut_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_gft_author FOREIGN KEY (author_id) REFERENCES habnut_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_group_forum_posts (
    id          INT UNSIGNED        NOT NULL AUTO_INCREMENT,
    thread_id   INT UNSIGNED        NOT NULL,
    author_id   INT UNSIGNED        NOT NULL,
    body        TEXT                NOT NULL,
    deleted     TINYINT(1)          NOT NULL DEFAULT 0,
    created_at  DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at  DATETIME            NULL,
    PRIMARY KEY (id),
    KEY idx_gfp_thread (thread_id, deleted, created_at),
    CONSTRAINT fk_gfp_thread FOREIGN KEY (thread_id) REFERENCES habnut_group_forum_threads (id) ON DELETE CASCADE,
    CONSTRAINT fk_gfp_author FOREIGN KEY (author_id) REFERENCES habnut_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── BADGES ───────────────────────────────────────────────────────────────────

CREATE TABLE habnut_badges (
    id                      VARCHAR(32)     NOT NULL,
    name                    VARCHAR(128)    NOT NULL,
    description             VARCHAR(512)    NOT NULL DEFAULT '',
    sprite_id               VARCHAR(128)    NOT NULL,
    category                VARCHAR(32)     NOT NULL DEFAULT 'general',
    is_achievement_badge    TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_user_badges (
    id          BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    user_id     INT UNSIGNED        NOT NULL,
    badge_id    VARCHAR(32)         NOT NULL,
    slot_index  TINYINT UNSIGNED    NULL,
    earned_at   DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_badge (user_id, badge_id),
    KEY idx_badge_slot (user_id, slot_index),
    CONSTRAINT fk_ubadge_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ubadge_badge FOREIGN KEY (badge_id) REFERENCES habnut_badges (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── ACHIEVEMENTS ─────────────────────────────────────────────────────────────

CREATE TABLE habnut_achievements (
    id              VARCHAR(64)     NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    description     VARCHAR(512)    NOT NULL DEFAULT '',
    category        VARCHAR(32)     NOT NULL,
    levels_json     JSON            NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_user_achievements (
    id                  BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    user_id             INT UNSIGNED        NOT NULL,
    achievement_id      VARCHAR(64)         NOT NULL,
    current_level       TINYINT UNSIGNED    NOT NULL DEFAULT 0,
    current_progress    INT UNSIGNED        NOT NULL DEFAULT 0,
    completed_at        DATETIME            NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_achievement (user_id, achievement_id),
    KEY idx_ach_completed (user_id, completed_at),
    CONSTRAINT fk_uach_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_uach_ach FOREIGN KEY (achievement_id) REFERENCES habnut_achievements (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── QUESTS ───────────────────────────────────────────────────────────────────

CREATE TABLE habnut_quests (
    id                  VARCHAR(64)     NOT NULL,
    name                VARCHAR(128)    NOT NULL,
    description         VARCHAR(512)    NOT NULL DEFAULT '',
    type                ENUM('daily','weekly','seasonal','story','special') NOT NULL,
    objectives_json     JSON            NOT NULL,
    reward_credits      INT UNSIGNED    NOT NULL DEFAULT 0,
    reward_diamonds     INT UNSIGNED    NOT NULL DEFAULT 0,
    reward_nut_points   INT UNSIGNED    NOT NULL DEFAULT 0,
    reward_badge_id     VARCHAR(32)     NULL,
    available_from      DATETIME        NULL,
    expires_at          DATETIME        NULL,
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_quest_type (type, enabled, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_user_quests (
    id              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    user_id         INT UNSIGNED        NOT NULL,
    quest_id        VARCHAR(64)         NOT NULL,
    progress_json   JSON                NOT NULL DEFAULT (JSON_OBJECT()),
    accepted_at     DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    DATETIME            NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_quest (user_id, quest_id),
    CONSTRAINT fk_uquest_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_uquest_quest FOREIGN KEY (quest_id) REFERENCES habnut_quests (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
