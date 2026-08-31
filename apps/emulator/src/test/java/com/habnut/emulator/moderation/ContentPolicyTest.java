package com.habnut.emulator.moderation;

import com.habnut.emulator.db.DatabaseManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the content policy stops, and — just as importantly — what it does not.
 *
 * The hotel is for adults. Swearing and frank conversation between them is not
 * moderated, and a filter that quietly creeps into policing ordinary talk is a
 * worse outcome than no filter at all, so the permitted cases are tested as
 * carefully as the forbidden ones.
 */
@DisplayName("Content policy")
class ContentPolicyTest {

    private DatabaseManager db;
    private ContentPolicy policy;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:policy_" + System.nanoTime()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE,KEY,ACTION");
        ds.setUser("sa");
        db = new DatabaseManager(ds);

        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE habnut_content_rules (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    category VARCHAR(32) NOT NULL,
                    label VARCHAR(128) NOT NULL,
                    pattern VARCHAR(512) NOT NULL,
                    exempt_pattern VARCHAR(512),
                    match_mode VARCHAR(16) NOT NULL DEFAULT 'words',
                    action VARCHAR(8) NOT NULL DEFAULT 'mute',
                    severity TINYINT NOT NULL DEFAULT 3,
                    mute_minutes SMALLINT NOT NULL DEFAULT 1440,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE
                )""");
        }
        policy = new ContentPolicy(db);
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP ALL OBJECTS");
        }
    }

    private void rule(String category, String pattern, String exempt,
                      String mode, String action, int severity) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO habnut_content_rules "
                 + "(category, label, pattern, exempt_pattern, match_mode, action, severity, mute_minutes) "
                 + "VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setString(1, category);
            ps.setString(2, category + " rule");
            ps.setString(3, pattern);
            ps.setString(4, exempt);
            ps.setString(5, mode);
            ps.setString(6, action);
            ps.setInt(7, severity);
            ps.setInt(8, 1440);
            ps.executeUpdate();
        }
        policy.reload();
    }

    // ─── normalisation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("accents and invisible characters are stripped before matching")
    void stripsDecoration() {
        assertEquals("kill you", ContentPolicy.normalisePlain("kíll​ you"));
    }

    @Test
    @DisplayName("digits standing in for letters are put back")
    void undoesLeetSpeak() {
        assertEquals("abuse", ContentPolicy.normalise("4buse"));
        assertEquals("stop", ContentPolicy.normalise("5t0p"));
    }

    @Test
    @DisplayName("a long run of one letter collapses, a doubled letter does not")
    void collapsesOnlyLongRuns() {
        assertEquals("stop", ContentPolicy.normalisePlain("stoooooop"));
        // "hello" must not become "helo", or every rule written against real
        // words stops matching real words.
        assertEquals("hello", ContentPolicy.normalisePlain("hello"));
    }

    @Test
    @DisplayName("digits survive as digits as well as being decoded")
    void keepsBothFormsOfAMessage() {
        ContentPolicy.Forms forms = ContentPolicy.Forms.of("call 07700900123");

        assertTrue(forms.plain().contains("07700900123"),
            "a rule about phone numbers needs the digits it was given");
        // The decoded form reads the same digits as the letters they stand in
        // for, which is what catches a word spelt with numbers.
        assertEquals("call ottoo9ooi2e", forms.decoded());
    }

    @Test
    @DisplayName("condensing removes the spacing used to break a term up")
    void condenseRemovesSeparators() {
        assertEquals("killyourself",
            ContentPolicy.condense(ContentPolicy.normalise("k i l l  y.o.u.r.s.e.l.f")));
    }

    // ─── what is permitted ──────────────────────────────────────────────────

    @Test
    @DisplayName("ordinary swearing is not the policy's business")
    void adultLanguagePasses() throws SQLException {
        rule("self_harm", "\\b(kys|kil+ yourself)\\b", null, "both", "mute", 5);

        for (String message : new String[] {
            "that furni is fucking gorgeous",
            "shit, I lost the trade",
            "this room is a bloody masterpiece",
            "god that was close",
        }) {
            assertTrue(policy.check(message).isEmpty(),
                "adult language should pass: " + message);
        }
    }

    @Test
    @DisplayName("frank conversation between adults is not the policy's business")
    void adultConversationPasses() throws SQLException {
        rule("minor_safety",
            "\\b(i(''m| am)?\\s*(a\\s*)?(1[0-7])\\b.{0,40}\\b(sexy|nude)\\b)", null, "words", "mute", 5);

        assertTrue(policy.check("we're both adults, want to take this to whispers").isEmpty());
        assertTrue(policy.check("she looks sexy in that outfit").isEmpty());
    }

    @Test
    @DisplayName("an exemption keeps a rule off an innocent phrase")
    void exemptionsPreventFalsePositives() throws SQLException {
        rule("threat", "\\bkil+ you\\b", "\\b(game|boss|round|match)\\b", "words", "mute", 4);

        assertTrue(policy.check("that boss will kill you in one hit").isEmpty(),
            "talk about a game is not a threat");
        assertTrue(policy.check("i will kill you").isPresent(),
            "the same words aimed at a person are");
    }

    // ─── what is stopped ────────────────────────────────────────────────────

    @Test
    @DisplayName("a term spelt out to dodge the filter is still caught")
    void catchesSpacedOutEvasion() throws SQLException {
        // A doubled letter may have been collapsed away by the time a rule
        // runs, so the pattern allows for either.
        rule("self_harm", "kil+ ?yourself|kys", null, "condensed", "mute", 5);

        for (String message : new String[] {
            "k y s",
            "k.y.s",
            "kiiiilll yourself",
            "KYS",
        }) {
            assertTrue(policy.check(message).isPresent(), "should be caught: " + message);
        }
    }

    @Test
    @DisplayName("the most serious rule is the one reported")
    void reportsTheWorstMatch() throws SQLException {
        rule("doxxing", "\\baddress\\b", null, "words", "flag", 2);
        rule("threat", "\\bi will hurt you\\b", null, "words", "mute", 5);

        Optional<ContentPolicy.Verdict> verdict =
            policy.check("give me your address or i will hurt you");

        assertTrue(verdict.isPresent());
        assertEquals("threat", verdict.get().category(),
            "a reviewer should see the threat first, not the lesser rule");
    }

    @Test
    @DisplayName("a flagging rule opens a case without silencing anybody")
    void flagRulesDoNotMute() throws SQLException {
        rule("doxxing", "\\b\\d{7,}\\b", null, "words", "flag", 2);

        ContentPolicy.Verdict verdict = policy.check("call me on 07700900123").orElseThrow();
        assertEquals(ContentPolicy.Action.FLAG, verdict.action());
        assertFalse(verdict.shouldMute(), "a flag is for a human to read, not a mute");
    }

    // ─── robustness ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a rule that will not compile is skipped, not fatal")
    void survivesABrokenRule() throws SQLException {
        rule("threat", "([unclosed", null, "words", "mute", 5);
        rule("self_harm", "\\bkys\\b", null, "words", "mute", 5);

        assertEquals(1, policy.ruleCount(), "the broken rule should have been dropped");
        assertTrue(policy.check("kys").isPresent(), "the good rule should still work");
    }

    @Test
    @DisplayName("a disabled rule stops applying")
    void disabledRulesAreIgnored() throws SQLException {
        rule("threat", "\\bi will hurt you\\b", null, "words", "mute", 5);
        assertTrue(policy.check("i will hurt you").isPresent());

        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("UPDATE habnut_content_rules SET enabled = FALSE");
        }
        policy.reload();

        assertTrue(policy.check("i will hurt you").isEmpty());
    }

    @Test
    @DisplayName("an empty message is not a violation")
    void emptyMessagesPass() throws SQLException {
        rule("threat", ".*", null, "words", "mute", 5);

        assertTrue(policy.check("").isEmpty());
        assertTrue(policy.check("   ").isEmpty());
        assertTrue(policy.check(null).isEmpty());
    }
}
