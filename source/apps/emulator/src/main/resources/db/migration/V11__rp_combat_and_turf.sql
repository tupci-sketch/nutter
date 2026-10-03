-- Nutropolis combat and territory.
--
-- The RP world already models jobs, factions, crime, courts, prison, medical
-- and banking, but two systems every roleplay world is built around were
-- missing: armed conflict between characters, and territory that factions
-- fight over. Characters carried a health value that nothing could reduce.

-- ── weapons ─────────────────────────────────────────────────────────────────
CREATE TABLE habnut_rp_weapon_types (
    id             INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    code           VARCHAR(48)     NOT NULL,
    name           VARCHAR(64)     NOT NULL,
    category       ENUM('melee','sidearm','rifle','heavy','nonlethal') NOT NULL DEFAULT 'melee',
    damage         SMALLINT UNSIGNED NOT NULL DEFAULT 10,
    range_tiles    TINYINT UNSIGNED  NOT NULL DEFAULT 1,
    cooldown_ms    INT UNSIGNED      NOT NULL DEFAULT 2000,
    magazine_size  SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    licence_required TINYINT(1)      NOT NULL DEFAULT 1,
    price_rp_cash  INT UNSIGNED      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_weapon_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_rp_character_weapons (
    id           BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    character_id INT UNSIGNED      NOT NULL,
    weapon_id    INT UNSIGNED      NOT NULL,
    ammo         SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    equipped     TINYINT(1)        NOT NULL DEFAULT 0,
    acquired_at  DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_character_weapon (character_id, weapon_id),
    KEY idx_character_weapons (character_id),
    CONSTRAINT fk_char_weapons_char FOREIGN KEY (character_id)
        REFERENCES habnut_rp_characters (id) ON DELETE CASCADE,
    CONSTRAINT fk_char_weapons_type FOREIGN KEY (weapon_id)
        REFERENCES habnut_rp_weapon_types (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Every exchange is recorded. Combat drives arrests, medical records and
-- faction reputation, so the log is the evidence those systems reason from.
CREATE TABLE habnut_rp_combat_log (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    attacker_id   INT UNSIGNED    NOT NULL,
    victim_id     INT UNSIGNED    NOT NULL,
    weapon_id     INT UNSIGNED    NULL,
    room_id       INT UNSIGNED    NULL,
    damage        SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    victim_health_after SMALLINT   NOT NULL DEFAULT 0,
    was_fatal     TINYINT(1)      NOT NULL DEFAULT 0,
    occurred_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_combat_attacker (attacker_id, occurred_at),
    KEY idx_combat_victim (victim_id, occurred_at),
    CONSTRAINT fk_combat_attacker FOREIGN KEY (attacker_id)
        REFERENCES habnut_rp_characters (id) ON DELETE CASCADE,
    CONSTRAINT fk_combat_victim FOREIGN KEY (victim_id)
        REFERENCES habnut_rp_characters (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Downed characters are incapacitated rather than deleted; medical treatment
-- or a respawn timer brings them back.
ALTER TABLE habnut_rp_characters
    ADD COLUMN is_downed     TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN downed_at     DATETIME   NULL DEFAULT NULL,
    ADD COLUMN last_attack_at DATETIME  NULL DEFAULT NULL;

-- ── territory ───────────────────────────────────────────────────────────────
CREATE TABLE habnut_rp_turfs (
    id             INT UNSIGNED   NOT NULL AUTO_INCREMENT,
    code           VARCHAR(48)    NOT NULL,
    name           VARCHAR(64)    NOT NULL,
    description    VARCHAR(255)   NOT NULL DEFAULT '',
    room_id        INT UNSIGNED   NULL,
    income_per_hour INT UNSIGNED  NOT NULL DEFAULT 0,
    capture_seconds INT UNSIGNED  NOT NULL DEFAULT 300,
    owner_faction_id INT UNSIGNED NULL,
    captured_at    DATETIME       NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_turf_code (code),
    KEY idx_turf_owner (owner_faction_id),
    CONSTRAINT fk_turf_faction FOREIGN KEY (owner_faction_id)
        REFERENCES habnut_rp_factions (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- An in-progress capture. A contest is resolved when the timer elapses with
-- the challenger still present, or abandoned if they leave.
CREATE TABLE habnut_rp_turf_captures (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    turf_id        INT UNSIGNED    NOT NULL,
    faction_id     INT UNSIGNED    NOT NULL,
    started_by      BIGINT UNSIGNED NOT NULL,
    started_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completes_at   DATETIME        NOT NULL,
    resolved_at    DATETIME        NULL DEFAULT NULL,
    outcome        ENUM('pending','captured','defended','abandoned') NOT NULL DEFAULT 'pending',
    PRIMARY KEY (id),
    KEY idx_capture_turf (turf_id, outcome),
    CONSTRAINT fk_capture_turf FOREIGN KEY (turf_id)
        REFERENCES habnut_rp_turfs (id) ON DELETE CASCADE,
    CONSTRAINT fk_capture_faction FOREIGN KEY (faction_id)
        REFERENCES habnut_rp_factions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── starting weapon catalogue ───────────────────────────────────────────────
INSERT INTO habnut_rp_weapon_types
    (code, name, category, damage, range_tiles, cooldown_ms, magazine_size, licence_required, price_rp_cash)
VALUES
    ('fists',     'Bare Hands',     'melee',     5,  1,  1200,  0, 0,     0),
    ('baton',     'Baton',          'melee',    12,  1,  1500,  0, 0,   250),
    ('taser',     'Taser',          'nonlethal',20,  3,  6000,  5, 1,   900),
    ('pistol',    'Pistol',         'sidearm',  25,  5,  1800, 12, 1,  2500),
    ('shotgun',   'Shotgun',        'heavy',    45,  3,  2800,  6, 1,  6000),
    ('rifle',     'Rifle',          'rifle',    35,  9,  2000, 30, 1,  9500);
