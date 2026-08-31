package com.habnut.emulator.moderation;

import com.habnut.emulator.db.DatabaseManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The life of an automatic mute: raised without a person, reviewed by one.
 *
 * The point of the review is that the machine's judgement is provisional. So the
 * things worth pinning down are that a case is always raised alongside the mute,
 * that overturning it actually frees the player, and that somebody who was
 * wrongly caught can say so — but not so often that saying so becomes another
 * way of shouting.
 */
@DisplayName("Automatic mutes")
class AutoModerationServiceTest {

    private static final long PLAYER = 42L;
    private static final long STAFF = 9L;

    private DatabaseManager db;
    private AutoModerationService service;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:automute_" + System.nanoTime()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE,KEY,ACTION");
        ds.setUser("sa");
        db = new DatabaseManager(ds);
        service = new AutoModerationService(db);

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
                    user_id INT NOT NULL,
                    mute_id INT NULL,
                    rule_id INT NULL,
                    category VARCHAR(32) NOT NULL,
                    message TEXT NOT NULL,
                    room_id INT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'pending_review',
                    reviewed_by_id INT NULL,
                    reviewed_at TIMESTAMP NULL,
                    review_notes VARCHAR(512) NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
            s.execute("""
                CREATE TABLE habnut_mute_help_requests (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    auto_mute_id BIGINT NOT NULL,
                    user_id INT NOT NULL,
                    message VARCHAR(512) NOT NULL,
                    handled_by_id INT NULL,
                    handled_at TIMESTAMP NULL,
                    response VARCHAR(512) NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP ALL OBJECTS");
        }
    }

    private ContentPolicy.Verdict verdict(String category, ContentPolicy.Action action, int minutes) {
        ContentPolicy.Rule rule = new ContentPolicy.Rule(
            1L, category, category + " rule", Pattern.compile("x"), null,
            "words", action, 5, minutes);
        return new ContentPolicy.Verdict(rule, category, action, 5, minutes);
    }

    // ─── raising ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a mute and a case for it are raised together")
    void muteRaisesACase() throws SQLException {
        Optional<AutoModerationService.MuteNotice> notice = service.mute(
            PLAYER, verdict("threat", ContentPolicy.Action.MUTE, 60),
            "i will hurt you", 5L);

        assertTrue(notice.isPresent());
        assertEquals("threat", notice.get().category());
        assertTrue(notice.get().canAskForHelp());

        assertEquals(1, count("habnut_mutes"));
        assertEquals(1, count("habnut_auto_mutes"));

        // A mute nobody can explain would be worse than no mute at all.
        try (Connection c = db.getConnection();
             ResultSet rs = c.createStatement().executeQuery(
                 "SELECT mute_id, message, status FROM habnut_auto_mutes")) {
            assertTrue(rs.next());
            assertNotNull(rs.getObject("mute_id"));
            assertEquals("i will hurt you", rs.getString("message"),
                "a reviewer should judge what was actually said");
            assertEquals("pending_review", rs.getString("status"));
        }
    }

    @Test
    @DisplayName("an automatic mute names no staff member")
    void automaticMutesHaveNoAuthor() throws SQLException {
        service.mute(PLAYER, verdict("scam", ContentPolicy.Action.MUTE, 60), "send me your password", null);

        try (Connection c = db.getConnection();
             ResultSet rs = c.createStatement().executeQuery(
                 "SELECT muted_by_id, source FROM habnut_mutes")) {
            assertTrue(rs.next());
            assertNull(rs.getObject("muted_by_id"), "nobody muted them; the policy did");
            assertEquals("automatic", rs.getString("source"));
        }
    }

    @Test
    @DisplayName("a mute carries a fallback expiry so a forgotten case does not last forever")
    void muteExpiresOnItsOwn() throws SQLException {
        service.mute(PLAYER, verdict("threat", ContentPolicy.Action.MUTE, 90), "x", null);

        try (Connection c = db.getConnection();
             ResultSet rs = c.createStatement().executeQuery(
                 "SELECT expires_at FROM habnut_mutes")) {
            assertTrue(rs.next());
            Instant expires = rs.getTimestamp("expires_at").toInstant();
            long minutes = Duration.between(Instant.now(), expires).toMinutes();
            assertTrue(minutes >= 88 && minutes <= 91, "expected about 90 minutes, got " + minutes);
        }
    }

    @Test
    @DisplayName("a flagged message opens a case without silencing anybody")
    void flagsDoNotMute() throws SQLException {
        service.flag(PLAYER, verdict("doxxing", ContentPolicy.Action.FLAG, 60), "07700900123", null);

        assertEquals(0, count("habnut_mutes"), "a flag is for a human to read");
        assertEquals(1, count("habnut_auto_mutes"));
    }

    // ─── review ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("overturning a case lifts the mute with it")
    void overturningFreesThePlayer() throws SQLException {
        long caseId = service.mute(PLAYER, verdict("threat", ContentPolicy.Action.MUTE, 600), "x", null)
            .orElseThrow().caseId();

        assertTrue(service.review(caseId, STAFF, false, "Talking about a game"));

        // A decision in the player's favour that leaves them muted has decided
        // nothing.
        try (Connection c = db.getConnection();
             ResultSet rs = c.createStatement().executeQuery(
                 "SELECT lifted_at, lifted_by_id FROM habnut_mutes")) {
            assertTrue(rs.next());
            assertNotNull(rs.getTimestamp("lifted_at"));
            assertEquals(STAFF, rs.getLong("lifted_by_id"));
        }
        assertEquals("overturned", statusOfOnlyCase());
    }

    @Test
    @DisplayName("upholding a case leaves the mute running")
    void upholdingKeepsTheMute() throws SQLException {
        long caseId = service.mute(PLAYER, verdict("hate", ContentPolicy.Action.MUTE, 600), "x", null)
            .orElseThrow().caseId();

        assertTrue(service.review(caseId, STAFF, true, "Stands"));

        try (Connection c = db.getConnection();
             ResultSet rs = c.createStatement().executeQuery("SELECT lifted_at FROM habnut_mutes")) {
            assertTrue(rs.next());
            assertNull(rs.getTimestamp("lifted_at"));
        }
        assertEquals("upheld", statusOfOnlyCase());
    }

    @Test
    @DisplayName("a case cannot be decided twice")
    void reviewIsFinal() {
        long caseId = service.mute(PLAYER, verdict("threat", ContentPolicy.Action.MUTE, 600), "x", null)
            .orElseThrow().caseId();

        assertTrue(service.review(caseId, STAFF, true, "Stands"));
        assertFalse(service.review(caseId, STAFF, false, "Changed my mind"),
            "a settled case should not quietly reopen");
    }

    @Test
    @DisplayName("waiting cases are counted for the staff tools")
    void countsWaitingCases() {
        service.mute(PLAYER, verdict("threat", ContentPolicy.Action.MUTE, 60), "x", null);
        service.mute(PLAYER + 1, verdict("scam", ContentPolicy.Action.MUTE, 60), "y", null);
        assertEquals(2, service.pendingCaseCount());

        long caseId = service.openCaseFor(PLAYER).orElseThrow().id();
        service.review(caseId, STAFF, true, null);
        assertEquals(1, service.pendingCaseCount());
    }

    // ─── asking for help ────────────────────────────────────────────────────

    @Test
    @DisplayName("a player muted by the policy can ask for a person to look")
    void mutedPlayerCanAskForHelp() {
        service.mute(PLAYER, verdict("threat", ContentPolicy.Action.MUTE, 600), "x", null);

        assertInstanceOf(AutoModerationService.HelpResult.Sent.class,
            service.requestHelp(PLAYER, "I was talking about a game"));
    }

    @Test
    @DisplayName("a second request inside the window is refused, with the wait")
    void helpIsRateLimited() {
        service.mute(PLAYER, verdict("threat", ContentPolicy.Action.MUTE, 600), "x", null);
        service.requestHelp(PLAYER, "First ask");

        AutoModerationService.HelpResult result = service.requestHelp(PLAYER, "Second ask");

        var refused = assertInstanceOf(AutoModerationService.HelpResult.Refused.class, result);
        assertEquals(AutoModerationService.HelpRefusal.TOO_SOON, refused.reason());
        assertTrue(refused.retryAfter().toMinutes() >= 14,
            "the player should be told roughly how long to wait, got " + refused.retryAfter());
        assertEquals(1, count("habnut_mute_help_requests"));
    }

    @Test
    @DisplayName("the window reopens once it has passed")
    void helpAllowedAgainAfterTheWindow() throws SQLException {
        service.mute(PLAYER, verdict("threat", ContentPolicy.Action.MUTE, 600), "x", null);
        service.requestHelp(PLAYER, "First ask");

        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("UPDATE habnut_mute_help_requests "
                + "SET created_at = DATEADD('MINUTE', -16, CURRENT_TIMESTAMP)");
        }

        assertTrue(service.timeUntilHelpAllowed(PLAYER).isZero());
        assertInstanceOf(AutoModerationService.HelpResult.Sent.class,
            service.requestHelp(PLAYER, "Still muted, any news?"));
    }

    @Test
    @DisplayName("somebody a moderator muted is sent to the appeals route instead")
    void staffMutesHaveNoHelpRoute() throws SQLException {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO habnut_mutes (user_id, muted_by_id, reason, expires_at, source) "
                + "VALUES (" + PLAYER + ", " + STAFF + ", 'Told to stop', "
                + "DATEADD('HOUR', 1, CURRENT_TIMESTAMP), 'staff')");
        }

        var refused = assertInstanceOf(AutoModerationService.HelpResult.Refused.class,
            service.requestHelp(PLAYER, "Let me out"));
        assertEquals(AutoModerationService.HelpRefusal.NOT_AUTOMATICALLY_MUTED, refused.reason());
    }

    @Test
    @DisplayName("an empty message still reaches the queue")
    void emptyHelpMessagesAreStillSent() throws SQLException {
        service.mute(PLAYER, verdict("threat", ContentPolicy.Action.MUTE, 600), "x", null);

        assertInstanceOf(AutoModerationService.HelpResult.Sent.class, service.requestHelp(PLAYER, ""));

        // Somebody who cannot think what to write is still asking for help.
        try (Connection c = db.getConnection();
             ResultSet rs = c.createStatement().executeQuery(
                 "SELECT message FROM habnut_mute_help_requests")) {
            assertTrue(rs.next());
            assertFalse(rs.getString("message").isBlank());
        }
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private int count(String table) {
        try (Connection c = db.getConnection();
             ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
    }

    private String statusOfOnlyCase() throws SQLException {
        try (Connection c = db.getConnection();
             ResultSet rs = c.createStatement().executeQuery("SELECT status FROM habnut_auto_mutes")) {
            assertTrue(rs.next());
            return rs.getString("status");
        }
    }
}
