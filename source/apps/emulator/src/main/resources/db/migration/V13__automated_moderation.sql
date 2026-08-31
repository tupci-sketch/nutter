-- V13: Automated moderation — content rules, review-gated mutes, help requests
--
-- The hotel is for adults and talks like it. Swearing and frank conversation are
-- part of that and are not moderated. What is moderated is the small set of
-- things that are harmful whoever is listening: hatred aimed at who somebody is,
-- threats, anything sexualising a child, publishing somebody's private details,
-- pushing a person toward self-harm, and phishing for accounts.
--
-- A message that trips one of those does not simply vanish. The player is muted
-- straight away and a case is raised for a staff member to look at, because an
-- automatic judgement is a guess and somebody has to check it. The mute carries
-- a fallback expiry so a case nobody gets to does not silence a player forever.

-- ─── RULES ────────────────────────────────────────────────────────────────────

CREATE TABLE habnut_content_rules (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    category        ENUM('hate','threat','minor_safety','doxxing','self_harm','scam') NOT NULL,
    label           VARCHAR(128)    NOT NULL,
    -- A regular expression, matched against a normalised form of the message.
    pattern         VARCHAR(512)    NOT NULL,
    -- An expression that, when it also matches, means this was innocent after
    -- all. Without one, a rule for a word inevitably fires on the town whose
    -- name contains it.
    exempt_pattern  VARCHAR(512)    NULL,
    -- 'words' matches the message as written, with word boundaries intact.
    -- 'condensed' matches it with every space and punctuation mark removed,
    -- which catches a term spelt out one letter at a time. 'both' tries each.
    match_mode      ENUM('words','condensed','both') NOT NULL DEFAULT 'words',
    -- 'mute' silences and raises a case; 'flag' only raises the case, for rules
    -- that are worth a look but too broad to act on alone.
    action          ENUM('mute','flag') NOT NULL DEFAULT 'mute',
    severity        TINYINT UNSIGNED NOT NULL DEFAULT 3,
    -- How long the mute lasts if nobody reviews it.
    mute_minutes    SMALLINT UNSIGNED NOT NULL DEFAULT 1440,
    enabled         TINYINT(1)      NOT NULL DEFAULT 1,
    created_by_id   INT UNSIGNED    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_content_rule_enabled (enabled, severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── MUTES ────────────────────────────────────────────────────────────────────

-- An automatic mute has no staff member behind it, so the column that named one
-- becomes optional and the row records where the mute came from instead.
ALTER TABLE habnut_mutes
    MODIFY COLUMN muted_by_id INT UNSIGNED NULL;

ALTER TABLE habnut_mutes
    ADD COLUMN source ENUM('staff','automatic') NOT NULL DEFAULT 'staff';

CREATE TABLE habnut_auto_mutes (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         INT UNSIGNED    NOT NULL,
    mute_id         INT UNSIGNED    NULL,
    rule_id         INT UNSIGNED    NULL,
    category        VARCHAR(32)     NOT NULL,
    -- The message as sent, kept so a reviewer judges what actually happened
    -- rather than the rule's opinion of it.
    message         TEXT            NOT NULL,
    room_id         INT UNSIGNED    NULL,
    status          ENUM('pending_review','upheld','overturned','expired')
                    NOT NULL DEFAULT 'pending_review',
    reviewed_by_id  INT UNSIGNED    NULL,
    reviewed_at     DATETIME        NULL,
    review_notes    VARCHAR(512)    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_automute_status (status, created_at),
    KEY idx_automute_user (user_id, created_at),
    CONSTRAINT fk_automute_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_automute_rule FOREIGN KEY (rule_id) REFERENCES habnut_content_rules (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── HELP REQUESTS ────────────────────────────────────────────────────────────

-- Somebody muted by a machine needs a way to say so. One request every fifteen
-- minutes is enough to be heard and not enough to be a second way of shouting.
CREATE TABLE habnut_mute_help_requests (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    auto_mute_id    BIGINT UNSIGNED NOT NULL,
    user_id         INT UNSIGNED    NOT NULL,
    message         VARCHAR(512)    NOT NULL,
    handled_by_id   INT UNSIGNED    NULL,
    handled_at      DATETIME        NULL,
    response        VARCHAR(512)    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_help_user (user_id, created_at),
    KEY idx_help_open (handled_at, created_at),
    CONSTRAINT fk_help_case FOREIGN KEY (auto_mute_id) REFERENCES habnut_auto_mutes (id) ON DELETE CASCADE,
    CONSTRAINT fk_help_user FOREIGN KEY (user_id) REFERENCES habnut_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── STARTING RULES ───────────────────────────────────────────────────────────

-- These describe shapes of harmful messages rather than listing words, so they
-- work on the day the hotel opens and do not need a vocabulary of slurs sitting
-- in a public repository. A hotel adds its own terms through the staff tools,
-- where they belong: every hotel's list is different and every list needs
-- keeping up to date.
--
-- Nothing here matches ordinary swearing or adult conversation. That is
-- deliberate: it is not what these rules are for.

-- Two notes for whoever writes the next rule.
--
-- First: no backslashes. A backslash inside a SQL string means different things
-- to different databases — MariaDB reads it as an escape and H2 does not — so a
-- pattern written with one works on the hotel and silently fails everywhere
-- else, or the other way round. Java regular expressions can say everything
-- these rules need without one: [0-9] for a digit, [ ] for a space, and
-- (?<![a-z]) / (?![a-z]) for the edges of a word.
--
-- Second: long runs of one character are collapsed before a rule sees a
-- message, so "kiiiilll" cannot walk past a rule about "kill". A word with a
-- doubled letter therefore has to allow for the doubling being gone — "kil+"
-- rather than "kill". Rules are matched both as typed and with digits decoded
-- back to letters, so a rule can be about numbers or about words without having
-- to say which.

INSERT INTO habnut_content_rules
    (category, label, pattern, exempt_pattern, match_mode, action, severity, mute_minutes) VALUES

-- Threats aimed at a person, as opposed to talk about a game.
('threat', 'Threat of violence against a person',
 '(?<![a-z])i(''ll|''m|ll|m| am| will| gonna| going to)?[ ]*(will[ ]+)?(kil+|murder|stab|shoo?t|beat|bat+er|hurt)[ ]+(your|you|ur|u|yer)(?![a-z])',
 '(?<![a-z])(playing|game|match|round|boss|noob|snowstorm|battle ?ball|football|freeze|goalkeeper)(?![a-z])',
 'words', 'mute', 4, 1440),

('threat', 'Threat to find someone in person',
 '(?<![a-z])i(''ll|''m|ll|m| am| will)?[ ]*(gonna|going to|will)?[ ]*(find|come to|turn up at|show up at)[ ]+(your|you|ur)[ ]*(house|home|address|school|work)(?![a-z])',
 NULL, 'words', 'mute', 5, 4320),

-- Sexualising a child, or trying to move a child off the hotel. The highest
-- severity in the table, and the longest fallback mute, because nothing else
-- here is as urgent.
('minor_safety', 'Sexual talk involving a stated minor',
 '(?<![a-z])(i(''m|m| am)?[ ]*(a[ ]*)?(1[0-7]|[89])[ ]*(years?[ ]*old|yo|y/o)?(?![0-9]).{0,80}(sexy|horny|nudes?|naked|dick|tits|cum|fuck me)|(sexy|horny|nudes?|naked).{0,80}(1[0-7]|[89])[ ]*(years?[ ]*old|yo|y/o)(?![0-9]))',
 NULL, 'words', 'mute', 5, 10080),

('minor_safety', 'Moving a conversation about age to a private app',
 '(?<![a-z])(how old are you|what age are you|asl)(?![a-z]).{0,120}(?<![a-z])(snap|snapchat|kik|discord|whatsapp|telegram|insta|instagram)(?![a-z])',
 NULL, 'words', 'flag', 4, 1440),

('minor_safety', 'Telling somebody to keep a conversation secret',
 '(?<![a-z])(dont|don''t|do not)[ ]+tell[ ]+(your[ ]+|ur[ ]+)?(mum|mom|dad|parents|anyone)(?![a-z])',
 NULL, 'words', 'mute', 5, 10080),

-- Publishing somebody's private details.
('doxxing', 'Posting a phone number',
 '([+]?[0-9][0-9 ().-]{8,}[0-9])',
 '(?<![a-z])(credits?|coins?|diamonds?|price|room|id|badge)(?![a-z])',
 'words', 'flag', 3, 720),

('doxxing', 'Posting a street address',
 '(?<![a-z])[0-9]{1,4}[ ]+[a-z]+[ ]+(street|st|road|rd|avenue|ave|lane|ln|drive|dr|close|court|crescent)(?![a-z])',
 NULL, 'words', 'mute', 4, 1440),

('doxxing', 'Threatening to publish somebody''s details',
 '(?<![a-z])(dox+|leak)(ing|ed)?[ ]+(your|you|ur|u|their|his|her)(?![a-z])',
 NULL, 'both', 'mute', 4, 2880),

-- Pushing somebody toward self-harm. Talking about your own struggles is not
-- caught here; telling somebody else to hurt themselves is.
('self_harm', 'Telling somebody to end their life',
 '(?<![a-z])(kys|kil+[ ]?yourself|kil+[ ]?urself|go die|end[ ]?yourself|hang[ ]?yourself|neck[ ]?yourself)(?![a-z])',
 NULL, 'both', 'mute', 5, 4320),

('self_harm', 'Encouraging self-injury',
 '(?<![a-z])(you should|u should|go)[ ]+(cut|harm|hurt)[ ]+(yourself|urself|ur[ ]?self)(?![a-z])',
 NULL, 'words', 'mute', 5, 4320),

-- Taking accounts.
('scam', 'Asking for a password',
 '(?<![a-z])(what(''s| is)?[ ]+(your|ur)[ ]+(password|pass|pw)|send[ ]+(me[ ]+)?(your|ur)[ ]+(password|pass|pw)|give[ ]+me[ ]+(your|ur)[ ]+(password|pass|pw))(?![a-z])',
 NULL, 'words', 'mute', 4, 1440),

('scam', 'Claiming to be staff to obtain an account',
 '(?<![a-z])(i am|i''m|im)[ ]+(an?[ ]+)?(admin|administrator|mod|moderator|staff)(?![a-z]).{0,80}(?<![a-z])(password|pass|pw|login|log in|account details)(?![a-z])',
 NULL, 'words', 'mute', 4, 2880),

('scam', 'Pointing players at a sign-in page off the hotel',
 '(?<![a-z])(free[ ]+(credits|diamonds|coins)|credit generator)(?![a-z]).{0,80}(https?://|www[.])',
 NULL, 'words', 'mute', 3, 1440),

-- Hatred aimed at who somebody is. Written as a shape — a group named, then a
-- dehumanising or violent statement about it — so that it works without a list
-- of slurs, which every hotel keeps for itself.
('hate', 'Dehumanising a group of people',
 '(?<![a-z])(all|every)[ ]+([a-z]+[ ]+)?(people|men|women|jews|muslims|christians|blacks|whites|asians|gays|lesbians|trans(gender)?s?|immigrants|foreigners)(?![a-z]).{0,60}(should[ ]+(die|be[ ]+kil+ed|be[ ]+gas+ed|hang)|are[ ]+(vermin|animals|subhuman|scum)|deserve[ ]+to[ ]+die)',
 NULL, 'words', 'mute', 5, 10080),

('hate', 'Telling somebody they are not welcome for who they are',
 '(?<![a-z])(go back to (your|ur) (own )?country|(your|ur) kind (isn''t|is not|ain''t) welcome)(?![a-z])',
 NULL, 'words', 'mute', 4, 4320);
