-- V15: Align the schema with the services that read and write it
--
-- Every repository in the emulator catches its own SQLException, logs it and
-- carries on. That is right at runtime — one failing query should not take a
-- room down — but it means a column that does not exist produces silence rather
-- than a crash, and the only symptom is a feature that never does anything.
--
-- The schema guard used to read only the columns a SELECT names through an
-- alias, so nothing checked the bare column lists an INSERT uses. Extending it
-- turned up eight tables being written to with names that were never created.
-- Each of the following had never recorded a single row.
--
-- The services are internally consistent and covered by tests, so the schema
-- moves to meet them, except where one service had drifted away from a name the
-- rest of the codebase agreed on. That one is corrected in the service instead.

-- ─── PHOTOS ───────────────────────────────────────────────────────────────────

-- The camera stores a token identifying the full picture and a small preview
-- shown in a list, rather than one blob doing both jobs.
ALTER TABLE habnut_photos
    CHANGE COLUMN taker_user_id user_id       INT UNSIGNED NOT NULL,
    CHANGE COLUMN data_url      preview_data  MEDIUMTEXT   NULL,
    CHANGE COLUMN created_at    taken_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE habnut_photos
    ADD COLUMN image_token VARCHAR(64) NOT NULL DEFAULT '';

ALTER TABLE habnut_photos
    ADD UNIQUE KEY uq_photo_token (image_token);

-- ─── MACHINE IDS ──────────────────────────────────────────────────────────────

-- The machine itself was renamed in V10; the address it was last seen from was
-- not, so it went nowhere. Catching somebody returning on a new account needs
-- both, and a ban already records both.
ALTER TABLE habnut_machine_ids
    ADD COLUMN ip_address VARCHAR(45) NULL;

-- ─── WALL ITEMS ───────────────────────────────────────────────────────────────

-- Floor items were aligned in V10; the wall items beside them were missed, so
-- nothing hung on a wall has ever been saved.
ALTER TABLE habnut_wall_items
    CHANGE COLUMN base_item_id base_id       INT UNSIGNED NOT NULL,
    CHANGE COLUMN user_id      owner_id      INT UNSIGNED NOT NULL,
    CHANGE COLUMN wall_loc     wall_position VARCHAR(64)  NOT NULL DEFAULT '';

-- ─── GROUPS ───────────────────────────────────────────────────────────────────

-- A group's room is its home room, and a group has a type the way a faction has
-- a tag.
ALTER TABLE habnut_groups
    CHANGE COLUMN room_id home_room_id INT UNSIGNED NULL;

ALTER TABLE habnut_groups
    ADD COLUMN type VARCHAR(32) NOT NULL DEFAULT 'normal';

-- ─── WIRED VARIABLES ──────────────────────────────────────────────────────────

-- The wired engine names a variable's own fields apart from the SQL words they
-- would otherwise collide with. Its persistence tests build the table this way
-- by hand, which is how a suite full of passing tests sat on top of a table the
-- engine could never write to.
ALTER TABLE habnut_wired_variables
    CHANGE COLUMN name         var_name   VARCHAR(64)  NOT NULL,
    CHANGE COLUMN type         var_type   VARCHAR(16)  NOT NULL DEFAULT 'number',
    CHANGE COLUMN value_number num_value  DOUBLE       NOT NULL DEFAULT 0,
    CHANGE COLUMN value_text   text_value VARCHAR(512) NULL,
    CHANGE COLUMN value_bool   bool_value TINYINT(1)   NOT NULL DEFAULT 0;

-- ─── TRANSACTIONS ─────────────────────────────────────────────────────────────

-- The economy writes why a transaction happened. The column it wrote to was
-- called description, so every ledger row went in without its reason — on the
-- one table in the hotel that is supposed to be a complete record.
ALTER TABLE habnut_transactions
    CHANGE COLUMN description reason VARCHAR(255) NOT NULL DEFAULT '';

-- ─── TOURNAMENT TEAMS AND BRACKETS ────────────────────────────────────────────

-- Tournaments had participants and matches but no teams and no bracket, so the
-- half of the tournament service that runs a knockout had nothing underneath it.
CREATE TABLE habnut_tournament_teams (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    tournament_id   INT UNSIGNED    NOT NULL,
    team_name       VARCHAR(64)     NOT NULL,
    captain_id      INT UNSIGNED    NOT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- One team per captain per tournament: a captain entering twice would meet
    -- themselves in the bracket.
    UNIQUE KEY uq_team_captain (tournament_id, captain_id),
    KEY idx_team_tournament (tournament_id),
    CONSTRAINT fk_team_tournament FOREIGN KEY (tournament_id)
        REFERENCES habnut_tournaments (id) ON DELETE CASCADE,
    CONSTRAINT fk_team_captain FOREIGN KEY (captain_id)
        REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_tournament_bracket (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    tournament_id   INT UNSIGNED    NOT NULL,
    round           SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    match_number    SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    -- Null when a round is drawn before its feeding round has finished, which
    -- is how a bye reaches the next round.
    team_a_id       INT UNSIGNED    NULL,
    team_b_id       INT UNSIGNED    NULL,
    winner_id       INT UNSIGNED    NULL,
    status          ENUM('PENDING','IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'PENDING',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_bracket_slot (tournament_id, round, match_number),
    KEY idx_bracket_round (tournament_id, round, status),
    CONSTRAINT fk_bracket_tournament FOREIGN KEY (tournament_id)
        REFERENCES habnut_tournaments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
