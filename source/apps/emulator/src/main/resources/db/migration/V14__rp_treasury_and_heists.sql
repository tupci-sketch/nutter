-- V14: Faction treasuries and heists
--
-- Two things were missing from the criminal side of Nutropolis, and they are
-- the same thing twice: money a faction holds together.
--
-- Territory already produced an hourly income, and that income went nowhere —
-- it was a number the server could calculate and nothing could ever receive.
-- Heists, the thing a criminal faction actually organises around, did not exist
-- at all.
--
-- A treasury gives both somewhere to land, and gives police and government a
-- thing to take away: a fine against a faction is now a real punishment rather
-- than a note on a record.

-- ─── TREASURY ─────────────────────────────────────────────────────────────────

ALTER TABLE habnut_rp_factions
    ADD COLUMN treasury BIGINT UNSIGNED NOT NULL DEFAULT 0;

-- Every movement is recorded rather than only the balance, so a member can see
-- where their faction's money went and a moderator can see who moved it.
CREATE TABLE habnut_rp_treasury_log (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    faction_id      INT UNSIGNED    NOT NULL,
    -- Signed: a withdrawal is a negative amount, so the log sums to the balance.
    amount          BIGINT          NOT NULL,
    balance_after   BIGINT UNSIGNED NOT NULL,
    kind            ENUM('turf_income','heist','deposit','withdrawal','fine','payroll') NOT NULL,
    memo            VARCHAR(255)    NOT NULL DEFAULT '',
    actor_character_id INT UNSIGNED NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_treasury_faction (faction_id, created_at),
    CONSTRAINT fk_treasury_faction FOREIGN KEY (faction_id)
        REFERENCES habnut_rp_factions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- When territory last paid out, so a restart cannot be used to collect twice.
ALTER TABLE habnut_rp_turfs
    ADD COLUMN last_paid_at DATETIME NULL DEFAULT NULL;

-- ─── HEISTS ───────────────────────────────────────────────────────────────────

-- What can be robbed. Kept as rows rather than in code so a hotel can add a
-- target without a new build, and so the numbers that decide whether a heist is
-- worth attempting can be tuned while people are playing.
CREATE TABLE habnut_rp_heist_targets (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    code            VARCHAR(48)     NOT NULL,
    name            VARCHAR(64)     NOT NULL,
    description     VARCHAR(255)    NOT NULL DEFAULT '',
    room_id         INT UNSIGNED    NULL,
    -- A heist is a crew job. Below the minimum it cannot start at all.
    min_crew        TINYINT UNSIGNED NOT NULL DEFAULT 2,
    max_crew        TINYINT UNSIGNED NOT NULL DEFAULT 6,
    -- How long the crew has to hold the target before it pays.
    duration_seconds SMALLINT UNSIGNED NOT NULL DEFAULT 180,
    -- How long before the alarm reaches the police. The gap between this and
    -- the duration is the window police have to arrive, and is what makes a
    -- heist a contest rather than a timer.
    alarm_seconds   SMALLINT UNSIGNED NOT NULL DEFAULT 45,
    payout_min      INT UNSIGNED    NOT NULL DEFAULT 5000,
    payout_max      INT UNSIGNED    NOT NULL DEFAULT 15000,
    -- Police who must be on duty before it can be attempted, so a hotel with
    -- nobody policing is not simply free money.
    police_required TINYINT UNSIGNED NOT NULL DEFAULT 2,
    cooldown_minutes SMALLINT UNSIGNED NOT NULL DEFAULT 120,
    last_robbed_at  DATETIME        NULL DEFAULT NULL,
    enabled         TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_heist_target_code (code),
    KEY idx_heist_target_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_rp_heists (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    target_id       INT UNSIGNED    NOT NULL,
    faction_id      INT UNSIGNED    NOT NULL,
    leader_character_id INT UNSIGNED NOT NULL,
    state           ENUM('planning','in_progress','succeeded','foiled','abandoned')
                    NOT NULL DEFAULT 'planning',
    started_at      DATETIME        NULL DEFAULT NULL,
    alarm_at        DATETIME        NULL DEFAULT NULL,
    resolves_at     DATETIME        NULL DEFAULT NULL,
    ended_at        DATETIME        NULL DEFAULT NULL,
    payout          INT UNSIGNED    NOT NULL DEFAULT 0,
    -- Who stopped it, when somebody did.
    foiled_by_character_id INT UNSIGNED NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_heist_state (state, resolves_at),
    KEY idx_heist_faction (faction_id, created_at),
    CONSTRAINT fk_heist_target FOREIGN KEY (target_id)
        REFERENCES habnut_rp_heist_targets (id) ON DELETE CASCADE,
    CONSTRAINT fk_heist_faction FOREIGN KEY (faction_id)
        REFERENCES habnut_rp_factions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_rp_heist_crew (
    heist_id        BIGINT UNSIGNED NOT NULL,
    character_id    INT UNSIGNED    NOT NULL,
    -- What each member took away, filled in when the heist resolves.
    share           INT UNSIGNED    NOT NULL DEFAULT 0,
    joined_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (heist_id, character_id),
    KEY idx_heist_crew_char (character_id),
    CONSTRAINT fk_heist_crew_heist FOREIGN KEY (heist_id)
        REFERENCES habnut_rp_heists (id) ON DELETE CASCADE,
    CONSTRAINT fk_heist_crew_char FOREIGN KEY (character_id)
        REFERENCES habnut_rp_characters (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── STARTING TARGETS ─────────────────────────────────────────────────────────

-- Ordered so that the first job a new crew can manage is small and quick, and
-- the one worth organising around needs a real crew and a long hold.
INSERT INTO habnut_rp_heist_targets
    (code, name, description, min_crew, max_crew, duration_seconds, alarm_seconds,
     payout_min, payout_max, police_required, cooldown_minutes) VALUES
    ('corner_shop', 'Corner Shop',
     'A till and a nervous shopkeeper. Quick money, and the police will hear about it.',
     1, 3, 60, 20, 400, 1200, 1, 20),
    ('jewellers', 'Jeweller''s',
     'Glass cases and a slow safe. Worth bringing somebody to watch the door.',
     2, 4, 120, 35, 2000, 5000, 1, 45),
    ('armoured_van', 'Armoured Van',
     'It stops for ninety seconds. So does everything else on that street.',
     3, 5, 90, 25, 4000, 9000, 2, 60),
    ('city_bank', 'City Bank Vault',
     'The one everybody talks about. A long hold, a loud alarm, and every officer in the city.',
     4, 8, 300, 60, 12000, 30000, 3, 240);
