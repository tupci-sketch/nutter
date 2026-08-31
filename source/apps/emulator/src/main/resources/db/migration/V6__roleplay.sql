-- V6: Nutropolis roleplay — characters, factions, vehicles, properties, jobs, businesses,
--     licences, crimes, courts, prison, bank, government, elections, medical, dispatch,
--     scenes, crafting, resources

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ─── CHARACTERS ───────────────────────────────────────────────────────────────

CREATE TABLE habnut_rp_characters (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    user_id         INT UNSIGNED    NOT NULL,
    name            VARCHAR(30)     NOT NULL,
    surname         VARCHAR(30)     NOT NULL,
    age             TINYINT UNSIGNED NOT NULL DEFAULT 25,
    biography       VARCHAR(512)    NOT NULL DEFAULT '',
    faction_id      INT UNSIGNED    NULL,
    job_id          INT UNSIGNED    NULL,
    health          TINYINT UNSIGNED NOT NULL DEFAULT 100,
    cash_balance    INT UNSIGNED    NOT NULL DEFAULT 0,
    bank_balance    INT UNSIGNED    NOT NULL DEFAULT 0,
    prison_expiry   DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_char_user (user_id),
    KEY idx_char_faction (faction_id),
    CONSTRAINT fk_char_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── FACTIONS ─────────────────────────────────────────────────────────────────

CREATE TABLE habnut_rp_factions (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    name            VARCHAR(64)     NOT NULL,
    tag             ENUM('police','fire','medical','government','criminal','civilian','business','media') NOT NULL,
    description     VARCHAR(512)    NOT NULL DEFAULT '',
    max_members     SMALLINT UNSIGNED NOT NULL DEFAULT 50,
    is_recruiting   TINYINT(1)      NOT NULL DEFAULT 1,
    badge_id        VARCHAR(32)     NOT NULL DEFAULT '',
    leader_id       INT UNSIGNED    NULL,
    hq_room_id      INT UNSIGNED    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_faction_tag (tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_rp_faction_members (
    faction_id      INT UNSIGNED    NOT NULL,
    character_id    INT UNSIGNED    NOT NULL,
    rank            VARCHAR(64)     NOT NULL DEFAULT 'recruit',
    joined_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (faction_id, character_id),
    KEY idx_fm_char (character_id),
    CONSTRAINT fk_fm_faction FOREIGN KEY (faction_id) REFERENCES habnut_rp_factions (id) ON DELETE CASCADE,
    CONSTRAINT fk_fm_char FOREIGN KEY (character_id) REFERENCES habnut_rp_characters (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE habnut_rp_characters
    ADD CONSTRAINT fk_char_faction FOREIGN KEY (faction_id) REFERENCES habnut_rp_factions (id) ON DELETE SET NULL;

-- ─── VEHICLES ─────────────────────────────────────────────────────────────────

CREATE TABLE habnut_rp_vehicle_types (
    id                  TINYINT UNSIGNED    NOT NULL AUTO_INCREMENT,
    name                VARCHAR(64)         NOT NULL,
    speed_modifier      DECIMAL(4,2)        NOT NULL DEFAULT 1.00,
    capacity            TINYINT UNSIGNED    NOT NULL DEFAULT 1,
    requires_licence    VARCHAR(32)         NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO habnut_rp_vehicle_types (id, name, speed_modifier, capacity, requires_licence) VALUES
    (1, 'Car',          1.00, 4, 'driving'),
    (2, 'Motorcycle',   1.30, 2, 'motorcycle'),
    (3, 'Police Car',   1.20, 4, 'law_enforcement'),
    (4, 'Ambulance',    1.10, 6, 'medical'),
    (5, 'Fire Truck',   0.90, 8, 'fire_service'),
    (6, 'Bicycle',      0.70, 1, NULL),
    (7, 'Boat',         0.80, 6, 'marine');

CREATE TABLE habnut_rp_vehicles (
    id                  INT UNSIGNED        NOT NULL AUTO_INCREMENT,
    type_id             TINYINT UNSIGNED    NOT NULL,
    owner_character_id  INT UNSIGNED        NULL,
    licence_plate       VARCHAR(10)         NOT NULL,
    state               ENUM('parked','in_use','impounded') NOT NULL DEFAULT 'parked',
    room_id             INT UNSIGNED        NULL,
    x                   SMALLINT            NOT NULL DEFAULT 0,
    y                   SMALLINT            NOT NULL DEFAULT 0,
    created_at          DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_plate (licence_plate),
    KEY idx_vehicle_owner (owner_character_id),
    CONSTRAINT fk_vehicle_type FOREIGN KEY (type_id) REFERENCES habnut_rp_vehicle_types (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── PROPERTIES ───────────────────────────────────────────────────────────────

CREATE TABLE habnut_rp_properties (
    id                      INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    name                    VARCHAR(128)    NOT NULL,
    type                    ENUM('residence','business','government','land') NOT NULL DEFAULT 'residence',
    price                   INT UNSIGNED    NOT NULL DEFAULT 0,
    rent_per_week           INT UNSIGNED    NULL,
    owner_character_id      INT UNSIGNED    NULL,
    room_id                 INT UNSIGNED    NOT NULL,
    address                 VARCHAR(128)    NOT NULL,
    for_sale                TINYINT(1)      NOT NULL DEFAULT 0,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_prop_owner (owner_character_id),
    KEY idx_prop_for_sale (for_sale, type),
    CONSTRAINT fk_prop_room FOREIGN KEY (room_id) REFERENCES habnut_rooms (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── JOBS / SHIFTS ────────────────────────────────────────────────────────────

CREATE TABLE habnut_rp_jobs (
    id                  INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    title               VARCHAR(64)     NOT NULL,
    description         VARCHAR(512)    NOT NULL DEFAULT '',
    faction_id          INT UNSIGNED    NULL,
    salary              INT UNSIGNED    NOT NULL DEFAULT 0,
    salary_interval     ENUM('shift','daily','weekly') NOT NULL DEFAULT 'shift',
    requirements_json   JSON            NOT NULL DEFAULT (JSON_ARRAY()),
    max_openings        SMALLINT UNSIGNED NOT NULL DEFAULT 10,
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_job_faction (faction_id),
    CONSTRAINT fk_job_faction FOREIGN KEY (faction_id) REFERENCES habnut_rp_factions (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_rp_job_applications (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    character_id    INT UNSIGNED    NOT NULL,
    job_id          INT UNSIGNED    NOT NULL,
    status          ENUM('pending','accepted','rejected') NOT NULL DEFAULT 'pending',
    applied_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at     DATETIME        NULL,
    reviewed_by_id  INT UNSIGNED    NULL,
    PRIMARY KEY (id),
    KEY idx_jobapp_char (character_id),
    KEY idx_jobapp_job (job_id, status),
    CONSTRAINT fk_jobapp_char FOREIGN KEY (character_id) REFERENCES habnut_rp_characters (id) ON DELETE CASCADE,
    CONSTRAINT fk_jobapp_job FOREIGN KEY (job_id) REFERENCES habnut_rp_jobs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_rp_shifts (
    id              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    character_id    INT UNSIGNED        NOT NULL,
    job_id          INT UNSIGNED        NOT NULL,
    clock_in_at     DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    clock_out_at    DATETIME            NULL,
    pay_amount      INT UNSIGNED        NOT NULL DEFAULT 0,
    paid_at         DATETIME            NULL,
    PRIMARY KEY (id),
    KEY idx_shift_char (character_id, clock_in_at),
    CONSTRAINT fk_shift_char FOREIGN KEY (character_id) REFERENCES habnut_rp_characters (id) ON DELETE CASCADE,
    CONSTRAINT fk_shift_job FOREIGN KEY (job_id) REFERENCES habnut_rp_jobs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── BUSINESSES ───────────────────────────────────────────────────────────────

CREATE TABLE habnut_rp_businesses (
    id                  INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    name                VARCHAR(64)     NOT NULL,
    type                VARCHAR(32)     NOT NULL,
    owner_character_id  INT UNSIGNED    NOT NULL,
    cash_balance        INT UNSIGNED    NOT NULL DEFAULT 0,
    room_id             INT UNSIGNED    NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_biz_owner (owner_character_id),
    CONSTRAINT fk_biz_owner FOREIGN KEY (owner_character_id) REFERENCES habnut_rp_characters (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_rp_business_employees (
    business_id     INT UNSIGNED    NOT NULL,
    character_id    INT UNSIGNED    NOT NULL,
    role            VARCHAR(64)     NOT NULL DEFAULT 'employee',
    hired_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fired_at        DATETIME        NULL,
    PRIMARY KEY (business_id, character_id),
    CONSTRAINT fk_be_business FOREIGN KEY (business_id) REFERENCES habnut_rp_businesses (id) ON DELETE CASCADE,
    CONSTRAINT fk_be_char FOREIGN KEY (character_id) REFERENCES habnut_rp_characters (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── LICENCES ─────────────────────────────────────────────────────────────────

CREATE TABLE habnut_rp_licence_types (
    id              VARCHAR(32)     NOT NULL,
    name            VARCHAR(64)     NOT NULL,
    description     VARCHAR(255)    NOT NULL DEFAULT '',
    test_required   TINYINT(1)      NOT NULL DEFAULT 1,
    fee             INT UNSIGNED    NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO habnut_rp_licence_types (id, name, description, test_required, fee) VALUES
    ('driving',         'Driving Licence',      'Required to operate a car.',        1, 500),
    ('motorcycle',      'Motorcycle Licence',   'Required to operate a motorcycle.', 1, 300),
    ('marine',          'Marine Licence',       'Required to operate a boat.',       1, 400),
    ('firearms',        'Firearms Licence',     'Required to carry a firearm.',      1, 1000),
    ('medical',         'Medical Licence',      'Required to practise medicine.',    1, 2000),
    ('fire_service',    'Fire Service Licence', 'Required to operate fire apparatus.', 0, 0),
    ('law_enforcement', 'Law Enforcement Auth', 'Issued to sworn officers.',         0, 0),
    ('taxi',            'Taxi Operator Licence','Required to operate a taxi service.', 1, 200),
    ('pilot',           'Pilot Licence',        'Required to operate aircraft.',     1, 5000);

CREATE TABLE habnut_rp_licences (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    character_id    INT UNSIGNED    NOT NULL,
    licence_type    VARCHAR(32)     NOT NULL,
    issued_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      DATETIME        NULL,
    revoked_at      DATETIME        NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_char_licence (character_id, licence_type, revoked_at),
    KEY idx_lic_char (character_id),
    CONSTRAINT fk_lic_char FOREIGN KEY (character_id) REFERENCES habnut_rp_characters (id) ON DELETE CASCADE,
    CONSTRAINT fk_lic_type FOREIGN KEY (licence_type) REFERENCES habnut_rp_licence_types (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── CRIMES / ARRESTS / COURTS / PRISON ───────────────────────────────────────

CREATE TABLE habnut_rp_crimes (
    id                          INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    character_id                INT UNSIGNED    NOT NULL,
    type                        VARCHAR(64)     NOT NULL,
    description                 TEXT            NOT NULL,
    arrested_by_character_id    INT UNSIGNED    NULL,
    sentence                    VARCHAR(255)    NULL,
    recorded_at                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expunged_at                 DATETIME        NULL,
    PRIMARY KEY (id),
    KEY idx_crime_char (character_id, expunged_at),
    CONSTRAINT fk_crime_char FOREIGN KEY (character_id) REFERENCES habnut_rp_characters (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_rp_arrests (
    id                      INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    suspect_character_id    INT UNSIGNED    NOT NULL,
    officer_character_id    INT UNSIGNED    NOT NULL,
    crime_description       TEXT            NOT NULL,
    arrested_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at            DATETIME        NULL,
    released_at             DATETIME        NULL,
    PRIMARY KEY (id),
    KEY idx_arrest_suspect (suspect_character_id),
    CONSTRAINT fk_arrest_suspect FOREIGN KEY (suspect_character_id) REFERENCES habnut_rp_characters (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_rp_court_cases (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    crime_id        INT UNSIGNED    NOT NULL,
    defendant_id    INT UNSIGNED    NOT NULL,
    prosecutor_id   INT UNSIGNED    NULL,
    judge_id        INT UNSIGNED    NULL,
    verdict         ENUM('pending','guilty','not_guilty','dismissed') NOT NULL DEFAULT 'pending',
    sentence_type   VARCHAR(64)     NULL,
    sentence_hours  SMALLINT UNSIGNED NULL,
    fine_amount     INT UNSIGNED    NULL,
    scheduled_at    DATETIME        NULL,
    completed_at    DATETIME        NULL,
    PRIMARY KEY (id),
    KEY idx_case_defendant (defendant_id),
    KEY idx_case_verdict (verdict),
    CONSTRAINT fk_case_crime FOREIGN KEY (crime_id) REFERENCES habnut_rp_crimes (id),
    CONSTRAINT fk_case_defendant FOREIGN KEY (defendant_id) REFERENCES habnut_rp_characters (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_rp_prison (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    character_id    INT UNSIGNED    NOT NULL,
    crime_id        INT UNSIGNED    NULL,
    sentence_start  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sentence_end    DATETIME        NOT NULL,
    released_early  TINYINT(1)      NOT NULL DEFAULT 0,
    released_at     DATETIME        NULL,
    release_reason  VARCHAR(128)    NULL,
    PRIMARY KEY (id),
    KEY idx_prison_char (character_id, released_at),
    CONSTRAINT fk_prison_char FOREIGN KEY (character_id) REFERENCES habnut_rp_characters (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── BANK (RP ISOLATED — never crosses to classic currencies) ─────────────────

CREATE TABLE habnut_rp_bank_transactions (
    id                      BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    transaction_hash        CHAR(64)            NOT NULL,
    idempotency_key         CHAR(36)            NOT NULL,
    from_character_id       INT UNSIGNED        NULL,
    to_character_id         INT UNSIGNED        NULL,
    amount                  INT UNSIGNED        NOT NULL,
    type                    VARCHAR(64)         NOT NULL,
    description             VARCHAR(255)        NOT NULL,
    balance_before          INT UNSIGNED        NOT NULL,
    balance_after           INT UNSIGNED        NOT NULL,
    created_at              DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_rp_tx_hash (transaction_hash),
    UNIQUE KEY uq_rp_tx_idempotency (from_character_id, idempotency_key),
    KEY idx_rp_tx_from (from_character_id, created_at),
    KEY idx_rp_tx_to (to_character_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── GOVERNMENT / ELECTIONS ───────────────────────────────────────────────────

CREATE TABLE habnut_rp_government_offices (
    id                      TINYINT UNSIGNED    NOT NULL AUTO_INCREMENT,
    title                   VARCHAR(64)         NOT NULL,
    holder_character_id     INT UNSIGNED        NULL,
    elected_at              DATETIME            NULL,
    term_ends_at            DATETIME            NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO habnut_rp_government_offices (id, title) VALUES
    (1, 'Mayor'),
    (2, 'Deputy Mayor'),
    (3, 'Chief of Police'),
    (4, 'Fire Chief'),
    (5, 'Health Minister'),
    (6, 'Chief Justice');

CREATE TABLE habnut_rp_elections (
    id                  INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    office_id           TINYINT UNSIGNED NOT NULL,
    status              ENUM('upcoming','nomination','voting','complete','cancelled') NOT NULL DEFAULT 'upcoming',
    nomination_open_at  DATETIME        NOT NULL,
    voting_open_at      DATETIME        NOT NULL,
    voting_close_at     DATETIME        NOT NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_election_status (status, voting_close_at),
    CONSTRAINT fk_election_office FOREIGN KEY (office_id) REFERENCES habnut_rp_government_offices (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_rp_election_candidates (
    election_id     INT UNSIGNED    NOT NULL,
    character_id    INT UNSIGNED    NOT NULL,
    platform        TEXT            NOT NULL DEFAULT '',
    vote_count      INT UNSIGNED    NOT NULL DEFAULT 0,
    nominated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (election_id, character_id),
    CONSTRAINT fk_ec_election FOREIGN KEY (election_id) REFERENCES habnut_rp_elections (id) ON DELETE CASCADE,
    CONSTRAINT fk_ec_char FOREIGN KEY (character_id) REFERENCES habnut_rp_characters (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_rp_election_votes (
    election_id             INT UNSIGNED    NOT NULL,
    voter_character_id      INT UNSIGNED    NOT NULL,
    candidate_character_id  INT UNSIGNED    NOT NULL,
    voted_at                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (election_id, voter_character_id),
    CONSTRAINT fk_ev_election FOREIGN KEY (election_id) REFERENCES habnut_rp_elections (id),
    CONSTRAINT fk_ev_voter FOREIGN KEY (voter_character_id) REFERENCES habnut_rp_characters (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_rp_government_decrees (
    id                      INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    issued_by_character_id  INT UNSIGNED    NOT NULL,
    title                   VARCHAR(128)    NOT NULL,
    body                    TEXT            NOT NULL,
    issued_at               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at              DATETIME        NULL,
    PRIMARY KEY (id),
    KEY idx_decree_issued (issued_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_rp_laws (
    id                      INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    title                   VARCHAR(128)    NOT NULL,
    body                    TEXT            NOT NULL,
    enacted_by_character_id INT UNSIGNED    NOT NULL,
    enacted_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    repealed_at             DATETIME        NULL,
    PRIMARY KEY (id),
    KEY idx_law_active (repealed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── DISPATCH ─────────────────────────────────────────────────────────────────

CREATE TABLE habnut_rp_dispatch_calls (
    id                      INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    caller_character_id     INT UNSIGNED    NOT NULL,
    call_type               ENUM('police','fire','medical') NOT NULL,
    location                VARCHAR(128)    NOT NULL,
    description             TEXT            NOT NULL,
    priority                TINYINT UNSIGNED NOT NULL DEFAULT 2,
    status                  ENUM('pending','active','resolved') NOT NULL DEFAULT 'pending',
    assigned_character_id   INT UNSIGNED    NULL,
    accepted_at             DATETIME        NULL,
    resolved_at             DATETIME        NULL,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_dispatch_status (status, call_type, created_at),
    CONSTRAINT fk_dispatch_caller FOREIGN KEY (caller_character_id) REFERENCES habnut_rp_characters (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── MEDICAL ──────────────────────────────────────────────────────────────────

CREATE TABLE habnut_rp_medical_records (
    id                      INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    character_id            INT UNSIGNED    NOT NULL,
    treated_by_character_id INT UNSIGNED    NULL,
    condition_desc          VARCHAR(255)    NOT NULL,
    treatment               VARCHAR(255)    NOT NULL DEFAULT '',
    admitted_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    discharged_at           DATETIME        NULL,
    PRIMARY KEY (id),
    KEY idx_medical_char (character_id),
    CONSTRAINT fk_medical_char FOREIGN KEY (character_id) REFERENCES habnut_rp_characters (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── SCENES ───────────────────────────────────────────────────────────────────

CREATE TABLE habnut_rp_scenes (
    id                  INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    location_room_id    INT UNSIGNED    NOT NULL,
    host_character_id   INT UNSIGNED    NOT NULL,
    title               VARCHAR(128)    NOT NULL,
    description         TEXT            NOT NULL DEFAULT '',
    started_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at            DATETIME        NULL,
    participants_json   JSON            NOT NULL DEFAULT (JSON_ARRAY()),
    PRIMARY KEY (id),
    KEY idx_scene_room (location_room_id),
    CONSTRAINT fk_scene_room FOREIGN KEY (location_room_id) REFERENCES habnut_rooms (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── CRAFTING ─────────────────────────────────────────────────────────────────

CREATE TABLE habnut_rp_crafting_recipes (
    id                  INT UNSIGNED        NOT NULL AUTO_INCREMENT,
    name                VARCHAR(64)         NOT NULL,
    ingredients_json    JSON                NOT NULL,
    result_item_id      INT UNSIGNED        NULL,
    level_required      TINYINT UNSIGNED    NOT NULL DEFAULT 1,
    faction_required    VARCHAR(32)         NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_rp_crafted_items (
    id              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    character_id    INT UNSIGNED        NOT NULL,
    recipe_id       INT UNSIGNED        NOT NULL,
    crafted_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    item_id         BIGINT UNSIGNED     NULL,
    PRIMARY KEY (id),
    KEY idx_crafted_char (character_id),
    CONSTRAINT fk_crafted_char FOREIGN KEY (character_id) REFERENCES habnut_rp_characters (id) ON DELETE CASCADE,
    CONSTRAINT fk_crafted_recipe FOREIGN KEY (recipe_id) REFERENCES habnut_rp_crafting_recipes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── RESOURCES ────────────────────────────────────────────────────────────────

CREATE TABLE habnut_rp_resources (
    character_id    INT UNSIGNED    NOT NULL,
    resource_type   VARCHAR(32)     NOT NULL,
    amount          INT UNSIGNED    NOT NULL DEFAULT 0,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (character_id, resource_type),
    CONSTRAINT fk_res_char FOREIGN KEY (character_id) REFERENCES habnut_rp_characters (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
