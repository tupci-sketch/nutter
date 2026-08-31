-- V2: Games — matches, stats, leaderboards, tournaments

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE habnut_game_matches (
    id          BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    match_id    CHAR(36)            NOT NULL,
    game_type   VARCHAR(32)         NOT NULL,
    room_id     INT UNSIGNED        NOT NULL,
    state       ENUM('lobby','countdown','active','finished','cancelled') NOT NULL DEFAULT 'lobby',
    started_at  DATETIME            NULL,
    ended_at    DATETIME            NULL,
    created_at  DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_match_id (match_id),
    KEY idx_match_room (room_id),
    KEY idx_match_type (game_type, state),
    CONSTRAINT fk_match_room FOREIGN KEY (room_id) REFERENCES habnut_rooms (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_game_match_players (
    match_id    CHAR(36)        NOT NULL,
    user_id     INT UNSIGNED    NOT NULL,
    team_id     CHAR(1)         NULL,
    score       INT             NOT NULL DEFAULT 0,
    joined_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at     DATETIME        NULL,
    PRIMARY KEY (match_id, user_id),
    KEY idx_gmp_user (user_id),
    CONSTRAINT fk_gmp_user FOREIGN KEY (user_id) REFERENCES habnut_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_game_stats (
    id              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    user_id         INT UNSIGNED        NOT NULL,
    match_id        CHAR(36)            NOT NULL,
    game_type       VARCHAR(32)         NOT NULL,
    score           INT                 NOT NULL DEFAULT 0,
    kills           INT UNSIGNED        NOT NULL DEFAULT 0,
    deaths          INT UNSIGNED        NOT NULL DEFAULT 0,
    assists         INT UNSIGNED        NOT NULL DEFAULT 0,
    won             TINYINT(1)          NOT NULL DEFAULT 0,
    team_id         CHAR(1)             NULL,
    duration_ms     INT UNSIGNED        NOT NULL DEFAULT 0,
    recorded_at     DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_stats_user (user_id, game_type),
    KEY idx_stats_type (game_type, score),
    CONSTRAINT fk_stats_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_leaderboards (
    id              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    user_id         INT UNSIGNED        NOT NULL,
    game_type       VARCHAR(32)         NOT NULL,
    period          ENUM('all_time','season','week') NOT NULL DEFAULT 'all_time',
    total_score     BIGINT              NOT NULL DEFAULT 0,
    wins            INT UNSIGNED        NOT NULL DEFAULT 0,
    matches_played  INT UNSIGNED        NOT NULL DEFAULT 0,
    updated_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_lb_user_type_period (user_id, game_type, period),
    KEY idx_lb_rank (game_type, period, total_score DESC),
    CONSTRAINT fk_lb_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_tournaments (
    id                      INT UNSIGNED        NOT NULL AUTO_INCREMENT,
    name                    VARCHAR(128)        NOT NULL,
    description             VARCHAR(512)        NOT NULL DEFAULT '',
    game_type               VARCHAR(32)         NOT NULL,
    format                  ENUM('single_elimination','double_elimination','round_robin','swiss') NOT NULL DEFAULT 'single_elimination',
    status                  ENUM('upcoming','registration','active','finished','cancelled') NOT NULL DEFAULT 'upcoming',
    registration_open_at    DATETIME            NOT NULL,
    registration_close_at   DATETIME            NOT NULL,
    starts_at               DATETIME            NOT NULL,
    max_participants        SMALLINT UNSIGNED   NOT NULL DEFAULT 16,
    current_participants    SMALLINT UNSIGNED   NOT NULL DEFAULT 0,
    prize_credits           INT UNSIGNED        NOT NULL DEFAULT 0,
    prize_diamonds          INT UNSIGNED        NOT NULL DEFAULT 0,
    prize_badge_id          VARCHAR(32)         NULL,
    room_id                 INT UNSIGNED        NULL,
    created_by_id           INT UNSIGNED        NOT NULL,
    created_at              DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_trn_status (status, starts_at),
    CONSTRAINT fk_trn_creator FOREIGN KEY (created_by_id) REFERENCES habnut_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_tournament_participants (
    tournament_id   INT UNSIGNED        NOT NULL,
    user_id         INT UNSIGNED        NOT NULL,
    registered_at   DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    eliminated_at   DATETIME            NULL,
    final_rank      SMALLINT UNSIGNED   NULL,
    PRIMARY KEY (tournament_id, user_id),
    KEY idx_tp_user (user_id),
    CONSTRAINT fk_tp_tournament FOREIGN KEY (tournament_id) REFERENCES habnut_tournaments (id) ON DELETE CASCADE,
    CONSTRAINT fk_tp_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_tournament_matches (
    id              INT UNSIGNED        NOT NULL AUTO_INCREMENT,
    tournament_id   INT UNSIGNED        NOT NULL,
    round           SMALLINT UNSIGNED   NOT NULL,
    player_a_id     INT UNSIGNED        NULL,
    player_b_id     INT UNSIGNED        NULL,
    winner_id       INT UNSIGNED        NULL,
    score_a         SMALLINT            NULL,
    score_b         SMALLINT            NULL,
    scheduled_at    DATETIME            NULL,
    completed_at    DATETIME            NULL,
    PRIMARY KEY (id),
    KEY idx_tm_tournament (tournament_id, round),
    CONSTRAINT fk_tm_tournament FOREIGN KEY (tournament_id) REFERENCES habnut_tournaments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
