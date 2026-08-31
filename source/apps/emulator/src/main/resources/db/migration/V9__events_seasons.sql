-- V9: Events, seasons, and supporting tables for Phase 16

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ─── ROOM SOUND STATE ─────────────────────────────────────────────────────────

CREATE TABLE habnut_room_sound_state (
    room_id             INT UNSIGNED    NOT NULL,
    current_track_idx   TINYINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (room_id),
    CONSTRAINT fk_rss_room FOREIGN KEY (room_id) REFERENCES habnut_rooms (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── SEASONS ──────────────────────────────────────────────────────────────────

CREATE TABLE habnut_seasons (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    name            VARCHAR(64)     NOT NULL,
    slug            VARCHAR(32)     NOT NULL UNIQUE,
    starts_at       DATETIME        NOT NULL,
    ends_at         DATETIME        NOT NULL,
    description     TEXT            NOT NULL DEFAULT '',
    rewards_json    JSON            NOT NULL DEFAULT (JSON_ARRAY()),
    active          TINYINT(1)      NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_season_active (active, starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── SEASON PARTICIPATION ─────────────────────────────────────────────────────

CREATE TABLE habnut_season_participants (
    season_id       INT UNSIGNED    NOT NULL,
    user_id         INT UNSIGNED    NOT NULL,
    points          INT UNSIGNED    NOT NULL DEFAULT 0,
    claimed_rewards TINYINT(1)      NOT NULL DEFAULT 0,
    joined_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (season_id, user_id),
    CONSTRAINT fk_sp_season FOREIGN KEY (season_id) REFERENCES habnut_seasons (id) ON DELETE CASCADE,
    CONSTRAINT fk_sp_user   FOREIGN KEY (user_id)   REFERENCES habnut_users (id)   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── COMPETITION PARTICIPANTS ─────────────────────────────────────────────────

CREATE TABLE habnut_competition_participants (
    competition_id  INT UNSIGNED    NOT NULL,
    user_id         INT UNSIGNED    NOT NULL,
    score           INT             NOT NULL DEFAULT 0,
    rank            SMALLINT UNSIGNED NULL,
    registered_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (competition_id, user_id),
    CONSTRAINT fk_cp_comp FOREIGN KEY (competition_id) REFERENCES habnut_competitions (id) ON DELETE CASCADE,
    CONSTRAINT fk_cp_user FOREIGN KEY (user_id)        REFERENCES habnut_users (id)        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
