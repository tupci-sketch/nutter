-- V16: Align the remaining drifted tables, including the ones every login and
-- every room load depend on
--
-- The schema guard checked the columns a query names through an alias, and the
-- columns an INSERT lists. It did not check the columns a SELECT names bare,
-- which is how a query against a single table is naturally written — so the
-- most-used queries in the server were the ones nothing was checking.
--
-- Extending it to those found seven more services reading columns that do not
-- exist. Because every repository catches its own exception and returns an
-- empty result, none of this failed loudly. It presented as a hotel where
-- nobody could log in, no room had a floor, and pets and bots did not exist.
--
-- As before, the schema moves to meet the services where they are internally
-- consistent, and the service is corrected where it is the one disagreeing with
-- everything else.

-- ─── USERS ────────────────────────────────────────────────────────────────────

-- The emulator's own user lookup — the query every single login goes through —
-- selected four columns that were never created, so findByUsername and findById
-- threw on every call and nobody could authenticate at all.
--
-- Two of the four were the service's mistake and are corrected there. The other
-- two genuinely belong: a hotel records where a login came from, and the
-- website already keeps both.
ALTER TABLE habnut_users
    ADD COLUMN last_login DATETIME    NULL DEFAULT NULL,
    ADD COLUMN last_ip    VARCHAR(45) NULL DEFAULT NULL;

-- The website and the economy both call this seasonal_currency; only the
-- original schema called it seasonal.
ALTER TABLE habnut_users
    CHANGE COLUMN seasonal seasonal_currency INT UNSIGNED NOT NULL DEFAULT 0;

-- Columns the website keeps about an account. They move here because the
-- website is about to stop keeping a second copy of every player.
ALTER TABLE habnut_users
    ADD COLUMN machine_id        VARCHAR(128) NULL DEFAULT NULL,
    ADD COLUMN remember_token    VARCHAR(100) NULL DEFAULT NULL,
    ADD COLUMN email_verified_at DATETIME     NULL DEFAULT NULL;

ALTER TABLE habnut_users
    ADD KEY idx_users_machine (machine_id);

-- ─── ROOM MODELS ──────────────────────────────────────────────────────────────

-- A room model is the floor plan: without it a room has no tiles to stand on.
-- The repository preloads every model at boot and reads three columns the table
-- does not have, so the preload threw and every room came back without a shape.
ALTER TABLE habnut_room_models
    CHANGE COLUMN map_data       heightmap     TEXT             NOT NULL,
    CHANGE COLUMN door_direction door_rotation TINYINT UNSIGNED NOT NULL DEFAULT 2;

ALTER TABLE habnut_room_models
    ADD COLUMN max_visitors SMALLINT UNSIGNED NOT NULL DEFAULT 50;

-- ─── PETS ─────────────────────────────────────────────────────────────────────

-- Pets were stored as two JSON blobs and read as a dozen plain columns, so not
-- one pet query has ever returned a row. The service's shape wins: hunger and
-- happiness tick on a schedule and are compared and ordered on, which is not
-- something to do inside a JSON document.
ALTER TABLE habnut_pets
    CHANGE COLUMN type          pet_type VARCHAR(32)  NOT NULL,
    CHANGE COLUMN owner_user_id owner_id INT UNSIGNED NOT NULL;

ALTER TABLE habnut_pets
    ADD COLUMN figure_data     VARCHAR(255)     NOT NULL DEFAULT '',
    ADD COLUMN level           TINYINT UNSIGNED NOT NULL DEFAULT 1,
    ADD COLUMN xp              INT UNSIGNED     NOT NULL DEFAULT 0,
    ADD COLUMN happiness       TINYINT UNSIGNED NOT NULL DEFAULT 100,
    ADD COLUMN hunger          TINYINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN thirst          TINYINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN pos_x           SMALLINT         NOT NULL DEFAULT 0,
    ADD COLUMN pos_y           SMALLINT         NOT NULL DEFAULT 0,
    ADD COLUMN current_room_id INT UNSIGNED     NULL DEFAULT NULL;

ALTER TABLE habnut_pets
    ADD KEY idx_pets_room (current_room_id);

-- ─── BOTS ─────────────────────────────────────────────────────────────────────

-- Same story as pets: the service keeps a bot's position on the bot.
ALTER TABLE habnut_bots
    CHANGE COLUMN owner_user_id owner_id INT UNSIGNED NOT NULL,
    CHANGE COLUMN figure_string figure   VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE habnut_bots
    ADD COLUMN pos_x           SMALLINT     NOT NULL DEFAULT 0,
    ADD COLUMN pos_y           SMALLINT     NOT NULL DEFAULT 0,
    ADD COLUMN current_room_id INT UNSIGNED NULL DEFAULT NULL;

ALTER TABLE habnut_bots
    ADD KEY idx_bots_room (current_room_id);

-- ─── TOURNAMENTS ──────────────────────────────────────────────────────────────

-- V15 gave the tournament service the teams and bracket it had always queried.
-- These three finish the job: it runs on teams, so it counts teams.
ALTER TABLE habnut_tournaments
    CHANGE COLUMN max_participants     max_teams     SMALLINT UNSIGNED NOT NULL DEFAULT 16,
    CHANGE COLUMN current_participants current_teams SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    CHANGE COLUMN starts_at            start_at      DATETIME          NULL DEFAULT NULL;
