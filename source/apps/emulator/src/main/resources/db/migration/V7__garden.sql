-- V7: Community Garden — plots, plants, seasons, goals, contributions, harvest log

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE habnut_garden_seasons (
    id          INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    season      ENUM('spring','summer','autumn','winter') NOT NULL,
    started_at  DATETIME        NOT NULL,
    ends_at     DATETIME        NOT NULL,
    active      TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_season_active (active, season)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_garden_plants (
    id                      TINYINT UNSIGNED    NOT NULL AUTO_INCREMENT,
    type                    VARCHAR(32)         NOT NULL,
    name                    VARCHAR(64)         NOT NULL,
    growth_time_ms          BIGINT UNSIGNED     NOT NULL,
    watering_interval_ms    BIGINT UNSIGNED     NOT NULL,
    harvest_yield           TINYINT UNSIGNED    NOT NULL DEFAULT 1,
    seasonal_bonus_season   ENUM('spring','summer','autumn','winter') NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_plant_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO habnut_garden_plants
    (id, type, name, growth_time_ms, watering_interval_ms, harvest_yield, seasonal_bonus_season)
VALUES
    (1,  'sunflower',    'Sunflower',        14400000, 3600000, 3, 'summer'),
    (2,  'rosebush',     'Rosebush',         21600000, 7200000, 5, 'spring'),
    (3,  'oak_sapling',  'Oak Sapling',      86400000, 14400000, 8, NULL),
    (4,  'pumpkin',      'Pumpkin',          28800000, 7200000, 4, 'autumn'),
    (5,  'strawberry',   'Strawberry Plant', 7200000,  1800000, 2, 'spring'),
    (6,  'mushroom',     'Mushroom Cluster', 10800000, 3600000, 3, 'autumn'),
    (7,  'cactus',       'Cactus',           43200000, 18000000, 2, 'summer'),
    (8,  'bluebell',     'Bluebell',         14400000, 3600000, 3, 'spring'),
    (9,  'snapdragon',   'Snapdragon',       21600000, 7200000, 4, 'summer'),
    (10, 'ghost_plant',  'Ghost Plant',      86400000, 28800000, 10, 'autumn');

CREATE TABLE habnut_garden_plots (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    owner_user_id   INT UNSIGNED    NULL,
    plant_type      VARCHAR(32)     NULL,
    stage           ENUM('seed','sprout','growing','mature','ready','withered') NULL,
    planted_at      DATETIME        NULL,
    watered_at      DATETIME        NULL,
    ready_at        DATETIME        NULL,
    withered_at     DATETIME        NULL,
    harvest_yield   TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_plot_owner (owner_user_id),
    KEY idx_plot_ready (ready_at),
    KEY idx_plot_withered (withered_at),
    CONSTRAINT fk_plot_user FOREIGN KEY (owner_user_id) REFERENCES habnut_users (id) ON DELETE SET NULL,
    CONSTRAINT fk_plot_plant FOREIGN KEY (plant_type) REFERENCES habnut_garden_plants (type) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_garden_goals (
    id                  INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    description         VARCHAR(255)    NOT NULL,
    plant_type          VARCHAR(32)     NULL,
    target_count        INT UNSIGNED    NOT NULL,
    current_count       INT UNSIGNED    NOT NULL DEFAULT 0,
    reward_description  VARCHAR(255)    NOT NULL,
    season_id           INT UNSIGNED    NULL,
    completed_at        DATETIME        NULL,
    expires_at          DATETIME        NOT NULL,
    PRIMARY KEY (id),
    KEY idx_goal_season (season_id, completed_at),
    KEY idx_goal_expires (expires_at),
    CONSTRAINT fk_goal_season FOREIGN KEY (season_id) REFERENCES habnut_garden_seasons (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_garden_goal_contributions (
    goal_id         INT UNSIGNED    NOT NULL,
    user_id         INT UNSIGNED    NOT NULL,
    contribution    INT UNSIGNED    NOT NULL DEFAULT 0,
    contributed_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (goal_id, user_id),
    CONSTRAINT fk_gc_goal FOREIGN KEY (goal_id) REFERENCES habnut_garden_goals (id) ON DELETE CASCADE,
    CONSTRAINT fk_gc_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_garden_harvest_log (
    id                      BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    user_id                 INT UNSIGNED        NOT NULL,
    plot_id                 INT UNSIGNED        NOT NULL,
    plant_type              VARCHAR(32)         NOT NULL,
    yield                   TINYINT UNSIGNED    NOT NULL,
    nut_points_granted      INT UNSIGNED        NOT NULL DEFAULT 0,
    contributed_to_goal     TINYINT(1)          NOT NULL DEFAULT 0,
    harvested_at            DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_harvest_user (user_id, harvested_at),
    CONSTRAINT fk_harvest_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
