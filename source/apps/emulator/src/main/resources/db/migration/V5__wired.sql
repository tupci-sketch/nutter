-- V5: Wired 2.0 — items, variables, chests, execution log, stack exports

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE habnut_wired_items (
    id              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    furni_id        BIGINT UNSIGNED     NOT NULL,
    room_id         INT UNSIGNED        NOT NULL,
    wired_type      VARCHAR(64)         NOT NULL,
    category        ENUM('trigger','action','condition','selector') NOT NULL,
    config_json     JSON                NOT NULL DEFAULT (JSON_OBJECT()),
    created_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_wired_furni (furni_id),
    KEY idx_wired_room (room_id, category),
    KEY idx_wired_type (wired_type),
    CONSTRAINT fk_wired_room FOREIGN KEY (room_id) REFERENCES habnut_rooms (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_wired_variables (
    id              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    name            VARCHAR(64)         NOT NULL,
    scope           ENUM('room','user','global') NOT NULL,
    type            ENUM('number','text','bool') NOT NULL DEFAULT 'number',
    value_text      VARCHAR(1024)       NULL,
    value_number    DOUBLE              NULL DEFAULT 0,
    value_bool      TINYINT(1)          NULL DEFAULT 0,
    room_id         INT UNSIGNED        NULL,
    user_id         INT UNSIGNED        NULL,
    updated_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_var_room (name, scope, room_id, user_id),
    KEY idx_var_scope (scope, room_id),
    KEY idx_var_global (scope, name),
    CONSTRAINT fk_wvar_room FOREIGN KEY (room_id) REFERENCES habnut_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_wvar_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_wired_chests (
    id          BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    furni_id    BIGINT UNSIGNED     NOT NULL,
    room_id     INT UNSIGNED        NOT NULL,
    slots_json  JSON                NOT NULL DEFAULT (JSON_OBJECT()),
    created_at  DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_chest_furni (furni_id),
    KEY idx_chest_room (room_id),
    CONSTRAINT fk_chest_room FOREIGN KEY (room_id) REFERENCES habnut_rooms (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_wired_execution_log (
    id              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    room_id         INT UNSIGNED        NOT NULL,
    trigger_furni_id BIGINT UNSIGNED    NOT NULL,
    stack_id        VARCHAR(64)         NOT NULL,
    execution_id    VARCHAR(36)         NOT NULL,
    event           VARCHAR(64)         NOT NULL,
    data            JSON                NOT NULL DEFAULT (JSON_OBJECT()),
    created_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_wex_room (room_id, created_at),
    KEY idx_wex_execution (execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_wired_stack_exports (
    id              INT UNSIGNED        NOT NULL AUTO_INCREMENT,
    room_id         INT UNSIGNED        NOT NULL,
    exported_by_id  INT UNSIGNED        NOT NULL,
    name            VARCHAR(128)        NOT NULL,
    data_json       MEDIUMTEXT          NOT NULL,
    created_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_wse_room (room_id),
    KEY idx_wse_user (exported_by_id),
    CONSTRAINT fk_wse_room FOREIGN KEY (room_id) REFERENCES habnut_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_wse_user FOREIGN KEY (exported_by_id) REFERENCES habnut_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
