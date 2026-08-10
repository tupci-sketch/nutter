-- V4: Audit and moderation — audit_logs, actions, reports, chat_logs, bans, appeals, word_filter

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Immutable audit log — rows are NEVER updated or deleted; retained 365 days
CREATE TABLE habnut_audit_logs (
    id                  BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    performed_by_id     INT UNSIGNED        NOT NULL,
    performed_by_name   VARCHAR(25)         NOT NULL,
    performed_by_ip     VARCHAR(45)         NOT NULL,
    action              VARCHAR(128)        NOT NULL,
    target_type         VARCHAR(64)         NULL,
    target_id           VARCHAR(64)         NULL,
    target_name         VARCHAR(64)         NULL,
    before_state        JSON                NULL,
    after_state         JSON                NULL,
    metadata            JSON                NOT NULL DEFAULT (JSON_OBJECT()),
    session_id          VARCHAR(64)         NULL,
    room_id             INT UNSIGNED        NULL,
    world_id            ENUM('classic','nutropolis','system') NOT NULL DEFAULT 'system',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_audit_performer (performed_by_id, created_at),
    KEY idx_audit_action (action, created_at),
    KEY idx_audit_target (target_type, target_id),
    KEY idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── BANS ─────────────────────────────────────────────────────────────────────

CREATE TABLE habnut_bans (
    id                  INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    user_id             INT UNSIGNED    NOT NULL,
    banned_by_id        INT UNSIGNED    NOT NULL,
    reason              TEXT            NOT NULL,
    ban_type            ENUM('standard','ip','machine','account_family') NOT NULL DEFAULT 'standard',
    ip_address          VARCHAR(45)     NULL,
    machine_id_hash     VARCHAR(64)     NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at          DATETIME        NULL,
    lifted_at           DATETIME        NULL,
    lifted_by_id        INT UNSIGNED    NULL,
    PRIMARY KEY (id),
    KEY idx_ban_user (user_id, lifted_at, expires_at),
    KEY idx_ban_ip (ip_address, lifted_at),
    KEY idx_ban_machine (machine_id_hash, lifted_at),
    CONSTRAINT fk_ban_user FOREIGN KEY (user_id) REFERENCES habnut_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── MUTES ────────────────────────────────────────────────────────────────────

CREATE TABLE habnut_mutes (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    user_id         INT UNSIGNED    NOT NULL,
    muted_by_id     INT UNSIGNED    NOT NULL,
    reason          VARCHAR(255)    NOT NULL,
    room_id         INT UNSIGNED    NULL,
    expires_at      DATETIME        NOT NULL,
    lifted_at       DATETIME        NULL,
    lifted_by_id    INT UNSIGNED    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_mute_user (user_id, lifted_at, expires_at),
    CONSTRAINT fk_mute_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── MODERATION ACTIONS ───────────────────────────────────────────────────────

CREATE TABLE habnut_moderation_actions (
    id                  BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    performed_by_id     INT UNSIGNED        NOT NULL,
    target_id           INT UNSIGNED        NOT NULL,
    action_type         ENUM('mute','unmute','kick','ban','unban','warn','room_mute','room_unmute') NOT NULL,
    reason              TEXT                NOT NULL,
    duration_ms         BIGINT UNSIGNED     NULL,
    expires_at          DATETIME            NULL,
    room_id             INT UNSIGNED        NULL,
    reverted_at         DATETIME            NULL,
    reverted_by_id      INT UNSIGNED        NULL,
    created_at          DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_modact_target (target_id, created_at),
    KEY idx_modact_performer (performed_by_id, created_at),
    CONSTRAINT fk_modact_performer FOREIGN KEY (performed_by_id) REFERENCES habnut_users (id),
    CONSTRAINT fk_modact_target FOREIGN KEY (target_id) REFERENCES habnut_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── REPORTS ──────────────────────────────────────────────────────────────────

CREATE TABLE habnut_reports (
    id              INT UNSIGNED        NOT NULL AUTO_INCREMENT,
    reporter_id     INT UNSIGNED        NOT NULL,
    target_id       INT UNSIGNED        NOT NULL,
    category        ENUM('offensive_language','harassment','scamming','cheating','inappropriate_content','ban_evasion','other') NOT NULL,
    description     TEXT                NOT NULL,
    chat_context    JSON                NOT NULL DEFAULT (JSON_ARRAY()),
    room_id         INT UNSIGNED        NULL,
    room_name       VARCHAR(60)         NULL,
    status          ENUM('open','claimed','resolved','dismissed') NOT NULL DEFAULT 'open',
    claimed_by_id   INT UNSIGNED        NULL,
    resolution      TEXT                NULL,
    created_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at     DATETIME            NULL,
    PRIMARY KEY (id),
    KEY idx_report_status (status, created_at),
    KEY idx_report_target (target_id),
    KEY idx_report_reporter (reporter_id),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES habnut_users (id),
    CONSTRAINT fk_report_target FOREIGN KEY (target_id) REFERENCES habnut_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── CHAT LOGS ────────────────────────────────────────────────────────────────

CREATE TABLE habnut_chat_logs (
    id          BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    user_id     INT UNSIGNED        NOT NULL,
    username    VARCHAR(25)         NOT NULL,
    room_id     INT UNSIGNED        NOT NULL,
    room_name   VARCHAR(60)         NOT NULL,
    message     TEXT                NOT NULL,
    type        ENUM('chat','shout','whisper','command') NOT NULL DEFAULT 'chat',
    timestamp   DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_chat_user (user_id, timestamp),
    KEY idx_chat_room (room_id, timestamp),
    KEY idx_chat_time (timestamp),
    FULLTEXT KEY ft_chat_message (message)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── APPEALS ──────────────────────────────────────────────────────────────────

CREATE TABLE habnut_appeals (
    id                  INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    user_id             INT UNSIGNED    NOT NULL,
    ban_id              INT UNSIGNED    NOT NULL,
    message             TEXT            NOT NULL,
    status              ENUM('pending','accepted','rejected') NOT NULL DEFAULT 'pending',
    reviewed_by_id      INT UNSIGNED    NULL,
    review_note         TEXT            NULL,
    submitted_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at         DATETIME        NULL,
    PRIMARY KEY (id),
    KEY idx_appeal_status (status, submitted_at),
    KEY idx_appeal_user (user_id),
    CONSTRAINT fk_appeal_user FOREIGN KEY (user_id) REFERENCES habnut_users (id),
    CONSTRAINT fk_appeal_ban FOREIGN KEY (ban_id) REFERENCES habnut_bans (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── WORD FILTER ──────────────────────────────────────────────────────────────

CREATE TABLE habnut_word_filter (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    word            VARCHAR(128)    NOT NULL,
    severity        TINYINT UNSIGNED NOT NULL DEFAULT 1,
    action          ENUM('block','replace','log') NOT NULL DEFAULT 'replace',
    replacement     VARCHAR(128)    NULL,
    created_by_id   INT UNSIGNED    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_word (word),
    KEY idx_wf_severity (severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
