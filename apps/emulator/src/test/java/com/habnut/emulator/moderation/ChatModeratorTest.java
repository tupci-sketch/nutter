package com.habnut.emulator.moderation;

import com.habnut.emulator.db.DatabaseManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The gate every spoken message passes through.
 *
 * Before this existed, a muted player could carry on talking: the mute was
 * written to the database and never read on the way into a room. These fix the
 * order the three questions are asked in, because getting it wrong is silent —
 * the hotel looks fine and the mute simply does nothing.
 */
@DisplayName("Chat moderation")
class ChatModeratorTest {

    private static final long PLAYER = 42L;
    private static final long STAFF = 9L;

    private DatabaseManager db;
    private ChatModerator moderator;
    private AutoModerationService autoMod;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:chatmod_" + System.nanoTime()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE,KEY,ACTION");
        ds.setUser("sa");
        db = new DatabaseManager(ds);

        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE habnut_mutes (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    user_id INT NOT NULL,
                    muted_by_id INT NULL,
                    reason VARCHAR(255) NOT NULL,
                    room_id INT NULL,
                    expires_at TIMESTAMP NOT NULL,
                    lifted_at TIMESTAMP NULL,
                    lifted_by_id INT NULL,
                    source VARCHAR(16) NOT NULL DEFAULT 'staff',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
            s.execute("""
                CREATE TABLE habnut_auto_mutes (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id INT NOT NULL, mute_id INT NULL, rule_id INT NULL,
                    category VARCHAR(32) NOT NULL, message TEXT NOT NULL, room_id INT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'pending_review',
                    reviewed_by_id INT NULL, reviewed_at TIMESTAMP NULL,
                    review_notes VARCHAR(512) NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
            s.execute("""
                CREATE TABLE habnut_mute_help_requests (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    auto_mute_id BIGINT NOT NULL, user_id INT NOT NULL,
                    message VARCHAR(512) NOT NULL, handled_by_id INT NULL,
                    handled_at TIMESTAMP NULL, response VARCHAR(512) NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
            s.execute("""
                CREATE TABLE habnut_content_rules (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    category VARCHAR(32) NOT NULL, label VARCHAR(128) NOT NULL,
                    pattern VARCHAR(512) NOT NULL, exempt_pattern VARCHAR(512),
                    match_mode VARCHAR(16) NOT NULL DEFAULT 'words',
                    action VARCHAR(8) NOT NULL DEFAULT 'mute',
                    severity TINYINT NOT NULL DEFAULT 3,
                    mute_minutes SMALLINT NOT NULL DEFAULT 1440,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE
                )""");
            s.execute("""
                CREATE TABLE habnut_word_filter (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    word VARCHAR(128) NOT NULL, severity TINYINT NOT NULL DEFAULT 1,
                    action VARCHAR(16) NOT NULL DEFAULT 'replace',
                    replacement VARCHAR(128) NULL
                )""");
            s.execute("INSERT INTO habnut_word_filter (word, action, replacement) "
                + "VALUES ('spoiler', 'replace', '[hidden]')");
        }

        // Bound rather than inlined: a backslash in a SQL literal is read by the
        // database before the pattern ever reaches the policy.
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO habnut_content_rules "
                 + "(category, label, pattern, match_mode, action, severity, mute_minutes) "
                 + "VALUES ('threat', 'Threat', ?, 'words', 'mute', 5, 120)")) {
            ps.setString(1, "\\bi will hurt you\\b");
            ps.executeUpdate();
        }

        ContentPolicy policy = new ContentPolicy(db);
        autoMod = new AutoModerationService(db);
        moderator = new ChatModerator(db, policy, autoMod, new WordFilter(db));
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP ALL OBJECTS");
        }
    }

    private void mutePlayer(String source, String reason, int hoursFromNow) throws SQLException {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO habnut_mutes (user_id, muted_by_id, reason, expires_at, source) "
                + "VALUES (" + PLAYER + ", " + ("staff".equals(source) ? STAFF : "NULL") + ", '"
                + reason + "', DATEADD('HOUR', " + hoursFromNow + ", CURRENT_TIMESTAMP), '"
                + source + "')");
        }
    }

    // ─── ordinary talk ──────────────────────────────────────────────────────

    @Test
    @DisplayName("an ordinary message goes through")
    void ordinaryTalkPasses() {
        ChatModerator.Decision decision = moderator.moderate(PLAYER, "anyone want to trade?", 1L);

        var allow = assertInstanceOf(ChatModerator.Decision.Allow.class, decision);
        assertEquals("anyone want to trade?", allow.message());
    }

    @Test
    @DisplayName("adult language goes through untouched")
    void adultLanguagePasses() {
        var allow = assertInstanceOf(ChatModerator.Decision.Allow.class,
            moderator.moderate(PLAYER, "that trade was fucking outrageous", 1L));
        assertEquals("that trade was fucking outrageous", allow.message());
    }

    @Test
    @DisplayName("the word filter still gets its substitution in")
    void wordFilterStillApplies() {
        var allow = assertInstanceOf(ChatModerator.Decision.Allow.class,
            moderator.moderate(PLAYER, "big spoiler ahead", 1L));
        assertEquals("big [hidden] ahead", allow.message());
    }

    // ─── mutes ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a muted player does not get to talk")
    void mutedPlayersAreStopped() throws SQLException {
        mutePlayer("staff", "Told to stop", 1);

        var muted = assertInstanceOf(ChatModerator.Decision.Muted.class,
            moderator.moderate(PLAYER, "hello everyone", 1L));
        assertEquals("Told to stop", muted.reason());
        assertFalse(muted.automatic());
        assertFalse(muted.canAskForHelp(),
            "somebody a moderator muted has already had a person look at them");
    }

    @Test
    @DisplayName("an automatically muted player is offered a review")
    void automaticallyMutedPlayersMayAskForHelp() throws SQLException {
        mutePlayer("automatic", "Automatic: awaiting review", 1);

        var muted = assertInstanceOf(ChatModerator.Decision.Muted.class,
            moderator.moderate(PLAYER, "hello everyone", 1L));
        assertTrue(muted.automatic());
        assertTrue(muted.canAskForHelp());
    }

    @Test
    @DisplayName("an expired mute stops applying")
    void expiredMutesLetThePlayerBack() throws SQLException {
        mutePlayer("staff", "Served", -1);

        assertInstanceOf(ChatModerator.Decision.Allow.class,
            moderator.moderate(PLAYER, "am I back?", 1L));
    }

    @Test
    @DisplayName("a lifted mute stops applying immediately")
    void liftedMutesTakeEffectAtOnce() throws SQLException {
        mutePlayer("staff", "Reviewed", 5);
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("UPDATE habnut_mutes SET lifted_at = CURRENT_TIMESTAMP");
        }

        // A moderator lifting a mute has to work on the player's next line, not
        // whenever a cache decides to expire.
        assertInstanceOf(ChatModerator.Decision.Allow.class,
            moderator.moderate(PLAYER, "thanks", 1L));
    }

    // ─── the policy ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a harmful message is stopped and mutes its sender")
    void harmfulMessagesAreStoppedAndMuted() {
        var stopped = assertInstanceOf(ChatModerator.Decision.Stopped.class,
            moderator.moderate(PLAYER, "i will hurt you", 3L));

        assertEquals("threat", stopped.category());
        assertNotNull(stopped.notice());
        assertTrue(stopped.notice().canAskForHelp());

        // And the next thing they say is stopped by the mute, not the rule.
        assertInstanceOf(ChatModerator.Decision.Muted.class,
            moderator.moderate(PLAYER, "sorry", 3L));
    }

    @Test
    @DisplayName("the room the message was said in is kept with the case")
    void casesRecordTheRoom() throws SQLException {
        moderator.moderate(PLAYER, "i will hurt you", 77L);

        try (Connection c = db.getConnection();
             ResultSet rs = c.createStatement().executeQuery(
                 "SELECT room_id FROM habnut_auto_mutes")) {
            assertTrue(rs.next());
            assertEquals(77L, rs.getLong("room_id"));
        }
    }

    @Test
    @DisplayName("a mute lifted on review lets the player speak again")
    void reviewRestoresTheirVoice() {
        var stopped = assertInstanceOf(ChatModerator.Decision.Stopped.class,
            moderator.moderate(PLAYER, "i will hurt you", 1L));

        autoMod.review(stopped.notice().caseId(), STAFF, false, "Quoting a film");

        assertInstanceOf(ChatModerator.Decision.Allow.class,
            moderator.moderate(PLAYER, "told you it was a quote", 1L));
    }
}
