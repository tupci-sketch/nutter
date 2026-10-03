-- Room decoration, avatar expression, and alignment of habnut_rooms with the
-- columns the room repository reads.
--
-- RoomRepository selects access_type, max_visitors, score, is_promoted,
-- category and six decoration columns. habnut_rooms was created with
-- access_mode, max_users, rating and is_public, and carried no decoration
-- columns at all, so every room query raised an SQLException that the
-- repository logged and swallowed — the navigator returned nothing and no room
-- could be entered. The renames below make the schema match the queries.

-- ── align existing columns with the repository ──────────────────────────────
ALTER TABLE habnut_rooms
    CHANGE COLUMN access_mode access_type  TINYINT UNSIGNED  NOT NULL DEFAULT 0,
    CHANGE COLUMN max_users   max_visitors TINYINT UNSIGNED  NOT NULL DEFAULT 25,
    CHANGE COLUMN rating      score        INT UNSIGNED      NOT NULL DEFAULT 0,
    CHANGE COLUMN is_public   is_promoted  TINYINT(1)        NOT NULL DEFAULT 0;

-- The repository reads a category name rather than the numeric key.
ALTER TABLE habnut_rooms
    ADD COLUMN category VARCHAR(64) NOT NULL DEFAULT 'general' AFTER category_id;

UPDATE habnut_rooms
SET category = COALESCE(
    (SELECT c.name FROM habnut_room_categories c WHERE c.id = habnut_rooms.category_id),
    'general');

-- ── decoration ──────────────────────────────────────────────────────────────
-- Wallpaper, floor pattern and landscape are the three surfaces a room owner
-- can change. Thickness values follow the client's -2..1 scale, where 0 is the
-- default and -2 hides the surface entirely.
ALTER TABLE habnut_rooms
    ADD COLUMN wallpaper         VARCHAR(64) NOT NULL DEFAULT '0.0' AFTER model_id,
    ADD COLUMN floor_pattern     VARCHAR(64) NOT NULL DEFAULT '0.0' AFTER wallpaper,
    ADD COLUMN background_colour VARCHAR(64) NOT NULL DEFAULT '0.0' AFTER floor_pattern,
    ADD COLUMN landscape_colour  VARCHAR(64) NOT NULL DEFAULT '0.0' AFTER background_colour,
    ADD COLUMN hide_walls        TINYINT(1)  NOT NULL DEFAULT 0     AFTER landscape_colour,
    ADD COLUMN wall_height       TINYINT     NOT NULL DEFAULT -1    AFTER hide_walls,
    ADD COLUMN wall_thickness    VARCHAR(8)  NOT NULL DEFAULT '0'   AFTER wall_height,
    ADD COLUMN floor_thickness   VARCHAR(8)  NOT NULL DEFAULT '0'   AFTER wall_thickness;

-- ── avatar expression ───────────────────────────────────────────────────────
-- The effect a user currently has enabled, 0 for none.
ALTER TABLE habnut_users
    ADD COLUMN current_effect INT NOT NULL DEFAULT 0;

-- Effects a user owns. Permanent effects never expire; timed effects from the
-- catalogue stop being selectable once they do.
CREATE TABLE habnut_user_effects (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id      INT UNSIGNED    NOT NULL,
    effect_id    INT             NOT NULL,
    duration_s   INT             NOT NULL DEFAULT 0,
    activated_at DATETIME        NULL DEFAULT NULL,
    expires_at   DATETIME        NULL DEFAULT NULL,
    is_permanent TINYINT(1)      NOT NULL DEFAULT 1,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_effect (user_id, effect_id),
    KEY idx_user_effects_user (user_id),
    CONSTRAINT fk_user_effects_user FOREIGN KEY (user_id)
        REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The hand item a piece of furniture gives out when used, so a drinks machine
-- dispenses what it is stocked with rather than a hardcoded item.
ALTER TABLE habnut_items_base
    ADD COLUMN hand_item_id INT NOT NULL DEFAULT 0;

-- ── align the remaining tables with the columns their services read ─────────
-- The same drift that broke habnut_rooms affects six other tables. Each
-- service below selects column names that were never created, so every one of
-- its queries raised an SQLException that was logged and swallowed: profiles
-- rendered empty, furniture never loaded into a room, wired stacks never
-- restored, and marketplace listings never appeared. The schema is aligned to
-- the services rather than the reverse, because the service model is the more
-- complete one — wired in particular needs a definition code and an explicit
-- ordering that the original three columns could not express.

ALTER TABLE habnut_users
    CHANGE COLUMN figure_string figure        VARCHAR(255) NOT NULL DEFAULT '',
    CHANGE COLUMN last_activity last_seen     DATETIME     NULL DEFAULT NULL,
    CHANGE COLUMN created_at    member_since  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN    xp            INT UNSIGNED  NOT NULL DEFAULT 0,
    ADD COLUMN    online        TINYINT(1)    NOT NULL DEFAULT 0;

-- Renamed on their own, because MariaDB will not rename a column a foreign key
-- depends on inside a combined ALTER: it falls back to ALGORITHM=COPY and then
-- refuses, since a copy cannot carry the constraint across. RENAME COLUMN does
-- it properly and is understood by both MariaDB and the H2 the schema tests
-- run against.
ALTER TABLE habnut_floor_items RENAME COLUMN base_item_id TO base_id;
ALTER TABLE habnut_floor_items RENAME COLUMN user_id TO owner_id;

ALTER TABLE habnut_floor_items
    CHANGE COLUMN extra extra_data TEXT NULL;

ALTER TABLE habnut_wired_items
    CHANGE COLUMN wired_type  component_type  VARCHAR(16)  NOT NULL,
    CHANGE COLUMN config_json params_json     JSON         NULL,
    CHANGE COLUMN category    definition_code VARCHAR(64)  NOT NULL,
    ADD COLUMN    stack_order SMALLINT UNSIGNED NOT NULL DEFAULT 0;

-- Renamed on their own, because MariaDB will not rename a column a foreign key
-- depends on inside a combined ALTER: it falls back to ALGORITHM=COPY and then
-- refuses, since a copy cannot carry the constraint across. RENAME COLUMN does
-- it properly and is understood by both MariaDB and the H2 the schema tests
-- run against.
ALTER TABLE habnut_marketplace_listings RENAME COLUMN base_item_id TO base_id;

ALTER TABLE habnut_marketplace_listings
    CHANGE COLUMN item_id      inventory_item_id BIGINT UNSIGNED NOT NULL,
    CHANGE COLUMN price        price_credits     INT UNSIGNED NOT NULL,
    CHANGE COLUMN listed_at    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN    sold         TINYINT(1)        NOT NULL DEFAULT 0;

UPDATE habnut_marketplace_listings SET sold = 1 WHERE sold_at IS NOT NULL;

ALTER TABLE habnut_messages
    CHANGE COLUMN sent_at            created_at            DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHANGE COLUMN deleted_by_receiver deleted_by_recipient TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE habnut_user_quests
    CHANGE COLUMN progress_json progress     JSON     NULL,
    ADD COLUMN    abandoned_at  DATETIME     NULL DEFAULT NULL;

-- Renamed on their own, because MariaDB will not rename a column a foreign key
-- depends on inside a combined ALTER: it falls back to ALGORITHM=COPY and then
-- refuses, since a copy cannot carry the constraint across. RENAME COLUMN does
-- it properly and is understood by both MariaDB and the H2 the schema tests
-- run against.
ALTER TABLE habnut_user_achievements RENAME COLUMN achievement_id TO achievement_code;

ALTER TABLE habnut_machine_ids
    CHANGE COLUMN machine_id_hash machine_id VARCHAR(128) NOT NULL;

-- Renamed on their own, because MariaDB will not rename a column a foreign key
-- depends on inside a combined ALTER: it falls back to ALGORITHM=COPY and then
-- refuses, since a copy cannot carry the constraint across. RENAME COLUMN does
-- it properly and is understood by both MariaDB and the H2 the schema tests
-- run against.
ALTER TABLE habnut_items_inventory RENAME COLUMN base_item_id TO base_id;
ALTER TABLE habnut_items_inventory RENAME COLUMN user_id TO owner_id;

ALTER TABLE habnut_items_inventory
    CHANGE COLUMN extra extra_data TEXT NULL;

ALTER TABLE habnut_group_forum_threads
    CHANGE COLUMN subject title VARCHAR(255) NOT NULL;

-- habnut_bans keeps lifted_at/lifted_by_id: the moderation service already
-- matches the schema, and the two auth services that had drifted to
-- revoked_at were corrected instead.

-- Progression and catalogue tables carry the same drift. Achievements and
-- badges are addressed by a stable string code rather than a numeric id, so a
-- seeded achievement keeps its identity across installs.
ALTER TABLE habnut_achievements
    ADD COLUMN code         VARCHAR(64)  NOT NULL DEFAULT '' AFTER id,
    ADD COLUMN max_progress INT UNSIGNED NOT NULL DEFAULT 1,
    ADD COLUMN points       INT UNSIGNED NOT NULL DEFAULT 0;

UPDATE habnut_achievements SET code = CONCAT('ach_', id) WHERE code = '';

ALTER TABLE habnut_achievements
    ADD UNIQUE KEY uq_achievement_code (code);

ALTER TABLE habnut_user_achievements
    CHANGE COLUMN current_progress progress INT UNSIGNED NOT NULL DEFAULT 0;

ALTER TABLE habnut_badges
    ADD COLUMN code      VARCHAR(64)  NOT NULL DEFAULT '' AFTER id,
    ADD COLUMN image_url VARCHAR(512) NOT NULL DEFAULT '';

UPDATE habnut_badges SET code = CONCAT('badge_', id) WHERE code = '';

ALTER TABLE habnut_badges
    ADD UNIQUE KEY uq_badge_code (code);

-- slot_index records which of the five profile slots a badge occupies;
-- equipped records whether it is displayed at all. Both are read.
-- Renamed on their own, because MariaDB will not rename a column a foreign key
-- depends on inside a combined ALTER: it falls back to ALGORITHM=COPY and then
-- refuses, since a copy cannot carry the constraint across. RENAME COLUMN does
-- it properly and is understood by both MariaDB and the H2 the schema tests
-- run against.
-- The widening from VARCHAR(32) to VARCHAR(64) is restated in V17, which is
-- where every type that a rename used to carry is set.
ALTER TABLE habnut_user_badges RENAME COLUMN badge_id TO badge_code;

ALTER TABLE habnut_user_badges
    ADD COLUMN    equipped TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE habnut_catalogue_offers
    CHANGE COLUMN price_credits  credits_price  INT UNSIGNED NOT NULL DEFAULT 0,
    CHANGE COLUMN price_diamonds diamonds_price INT UNSIGNED NOT NULL DEFAULT 0,
    CHANGE COLUMN giftable       is_gift        TINYINT(1)   NOT NULL DEFAULT 1,
    CHANGE COLUMN enabled        is_visible     TINYINT(1)   NOT NULL DEFAULT 1,
    ADD COLUMN    base_id        INT UNSIGNED   NOT NULL DEFAULT 0 AFTER page_id;

-- Friendship rows gain a surrogate key and an acceptance flag so a pending
-- request and an established friendship are distinguishable in one table.
-- The pair was the primary key; it becomes a unique constraint so the row can
-- carry a surrogate id, which the friend service uses to address a single
-- friendship without needing both user ids.
-- Renamed on their own, because MariaDB will not rename a column a foreign key
-- depends on inside a combined ALTER: it falls back to ALGORITHM=COPY and then
-- refuses, since a copy cannot carry the constraint across. RENAME COLUMN does
-- it properly and is understood by both MariaDB and the H2 the schema tests
-- run against.
ALTER TABLE habnut_friends RENAME COLUMN user_id_a TO user_a;
ALTER TABLE habnut_friends RENAME COLUMN user_id_b TO user_b;

ALTER TABLE habnut_friends
    ADD COLUMN accepted TINYINT(1) NOT NULL DEFAULT 1;

ALTER TABLE habnut_friends DROP PRIMARY KEY;

ALTER TABLE habnut_friends
    ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;

ALTER TABLE habnut_friends
    ADD UNIQUE KEY uq_friend_pair (user_a, user_b);

ALTER TABLE habnut_group_forum_posts
    CHANGE COLUMN deleted hidden TINYINT(1) NOT NULL DEFAULT 0;

-- Quests are addressed by code like achievements, carry the rotation category
-- the quest service groups by, and record the progress needed to complete.
-- "type" distinguishes the objective kind; "category" is the rotation bucket
-- the quest service groups by. Both are read, so both are kept.
ALTER TABLE habnut_quests
    CHANGE COLUMN reward_badge_id badge_code        VARCHAR(64)  NULL DEFAULT NULL,
    ADD COLUMN    code            VARCHAR(64)       NOT NULL DEFAULT '' AFTER id,
    ADD COLUMN    category        VARCHAR(32)       NOT NULL DEFAULT 'daily',
    ADD COLUMN    required_progress INT UNSIGNED    NOT NULL DEFAULT 1;

UPDATE habnut_quests SET code = CONCAT('quest_', id) WHERE code = '';

ALTER TABLE habnut_quests
    ADD UNIQUE KEY uq_quest_code (code);
