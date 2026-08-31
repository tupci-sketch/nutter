-- V12: Forums — public boards and group boards in one place
--
-- Groups already had a forum inside the hotel. Players also want to read and
-- post from the website, and a hotel wants public boards that belong to nobody.
-- Rather than build a second forum for the website and leave the two disagreeing
-- about what a thread is, the group forum is generalised: a thread belongs to
-- either a public category or a group, and everything else about it is the same.

-- ─── CATEGORIES ───────────────────────────────────────────────────────────────

CREATE TABLE habnut_forum_categories (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    slug            VARCHAR(64)     NOT NULL,
    name            VARCHAR(96)     NOT NULL,
    description     VARCHAR(512)    NOT NULL DEFAULT '',
    -- Ranks gate who may read and who may post, so a staff-only board and an
    -- announcements board that everyone reads but only staff writes to are the
    -- same mechanism with different numbers.
    min_read_rank   TINYINT UNSIGNED NOT NULL DEFAULT 0,
    min_post_rank   TINYINT UNSIGNED NOT NULL DEFAULT 1,
    sort_order      SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    locked          TINYINT(1)      NOT NULL DEFAULT 0,
    thread_count    INT UNSIGNED    NOT NULL DEFAULT 0,
    post_count      INT UNSIGNED    NOT NULL DEFAULT 0,
    last_thread_id  INT UNSIGNED    NULL,
    last_post_at    DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_forum_cat_slug (slug),
    KEY idx_forum_cat_order (sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── THREADS AND POSTS ────────────────────────────────────────────────────────

ALTER TABLE habnut_group_forum_threads RENAME TO habnut_forum_threads;
ALTER TABLE habnut_group_forum_posts   RENAME TO habnut_forum_posts;

-- A thread now hangs off a category or a group. Exactly one of the two is set:
-- a thread with neither belongs nowhere and would be unreachable, and a thread
-- with both would appear twice.
ALTER TABLE habnut_forum_threads
    MODIFY COLUMN group_id INT UNSIGNED NULL;

ALTER TABLE habnut_forum_threads
    ADD COLUMN category_id     INT UNSIGNED    NULL AFTER group_id,
    ADD COLUMN views           INT UNSIGNED    NOT NULL DEFAULT 0,
    ADD COLUMN last_post_id    INT UNSIGNED    NULL,
    ADD COLUMN last_poster_id  INT UNSIGNED    NULL,
    ADD COLUMN updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE habnut_forum_threads
    ADD CONSTRAINT ck_forum_thread_home
        CHECK ((group_id IS NULL) <> (category_id IS NULL));

ALTER TABLE habnut_forum_threads
    ADD CONSTRAINT fk_forum_thread_category
        FOREIGN KEY (category_id) REFERENCES habnut_forum_categories (id) ON DELETE CASCADE;

ALTER TABLE habnut_forum_threads
    ADD KEY idx_forum_thread_category (category_id, hidden, pinned, last_reply_at);

-- Posts record who changed them and why, so a hidden post can be explained to
-- its author rather than simply vanishing.
ALTER TABLE habnut_forum_posts
    CHANGE COLUMN deleted_at hidden_at DATETIME NULL,
    ADD COLUMN edited_at      DATETIME     NULL,
    ADD COLUMN edited_by_id   INT UNSIGNED NULL,
    ADD COLUMN hidden_by_id   INT UNSIGNED NULL,
    ADD COLUMN hidden_reason  VARCHAR(255) NULL;

-- ─── FORUM STAFF ──────────────────────────────────────────────────────────────

-- Forum standing is separate from hotel rank. A player can be trusted to run a
-- board without being given moderator powers inside the hotel, and a hotel
-- moderator is not automatically the right person to run the fansite board.
CREATE TABLE habnut_forum_moderators (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    user_id         INT UNSIGNED    NOT NULL,
    -- 'global' covers every board; 'category' and 'group' scope the role to one.
    scope           ENUM('global','category','group') NOT NULL DEFAULT 'category',
    scope_id        INT UNSIGNED    NULL,
    -- A moderator hides, locks and pins. An administrator also edits other
    -- people's posts, moves threads between boards and appoints moderators.
    role            ENUM('moderator','administrator') NOT NULL DEFAULT 'moderator',
    granted_by_id   INT UNSIGNED    NOT NULL,
    granted_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_forum_mod (user_id, scope, scope_id),
    KEY idx_forum_mod_scope (scope, scope_id),
    CONSTRAINT fk_forum_mod_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_forum_mod_grantor FOREIGN KEY (granted_by_id) REFERENCES habnut_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── REPORTS AND SUBSCRIPTIONS ────────────────────────────────────────────────

CREATE TABLE habnut_forum_reports (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    post_id         INT UNSIGNED    NOT NULL,
    reporter_id     INT UNSIGNED    NOT NULL,
    reason          VARCHAR(512)    NOT NULL,
    status          ENUM('open','upheld','dismissed') NOT NULL DEFAULT 'open',
    handled_by_id   INT UNSIGNED    NULL,
    handled_at      DATETIME        NULL,
    notes           VARCHAR(512)    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- One report per person per post; reporting twice does not raise the queue.
    UNIQUE KEY uq_forum_report (post_id, reporter_id),
    KEY idx_forum_report_status (status, created_at),
    CONSTRAINT fk_forum_report_post FOREIGN KEY (post_id) REFERENCES habnut_forum_posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_forum_report_user FOREIGN KEY (reporter_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habnut_forum_subscriptions (
    thread_id       INT UNSIGNED    NOT NULL,
    user_id         INT UNSIGNED    NOT NULL,
    last_read_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notify          TINYINT(1)      NOT NULL DEFAULT 1,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (thread_id, user_id),
    KEY idx_forum_sub_user (user_id, notify),
    CONSTRAINT fk_forum_sub_thread FOREIGN KEY (thread_id) REFERENCES habnut_forum_threads (id) ON DELETE CASCADE,
    CONSTRAINT fk_forum_sub_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── SEED BOARDS ──────────────────────────────────────────────────────────────

-- A forum with no boards is a dead end, so a fresh hotel opens with the boards
-- every hotel ends up creating anyway.
INSERT INTO habnut_forum_categories (slug, name, description, min_read_rank, min_post_rank, sort_order) VALUES
    ('announcements', 'Announcements', 'News and notices from the hotel staff.', 0, 4, 10),
    ('general',       'General',       'Anything and everything about the hotel.', 0, 1, 20),
    ('help',          'Help and Support', 'Stuck on something? Ask here.', 0, 1, 30),
    ('rooms',         'Rooms and Building', 'Show off a build or ask for a hand with one.', 0, 1, 40),
    ('trading',       'Trading',       'Wanted, for sale, and swaps.', 0, 1, 50),
    ('nutropolis',    'Nutropolis',    'Roleplay talk: characters, factions and stories.', 0, 1, 60),
    ('staff-room',    'Staff Room',    'Private board for hotel staff.', 3, 3, 70);
