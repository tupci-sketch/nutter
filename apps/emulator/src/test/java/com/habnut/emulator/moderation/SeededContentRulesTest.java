package com.habnut.emulator.moderation;

import com.habnut.emulator.db.DatabaseManager;
import com.habnut.emulator.db.MigrationHarness;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The rules a hotel actually opens with.
 *
 * These load the shipped migration rather than test fixtures, because a rule
 * that works against a hand-written pattern and not against the one in the
 * migration protects nobody. Two things are being checked: that the rules catch
 * what they are for, and — the part that is easier to get wrong and worse to get
 * wrong — that they leave ordinary adult conversation alone.
 */
@DisplayName("Shipped content rules")
class SeededContentRulesTest {

    private static DatabaseManager db;
    private static ContentPolicy policy;

    @BeforeAll
    static void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:seededrules_" + System.nanoTime()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE,KEY,YEAR,MONTH,DAY,HOUR,MINUTE,SECOND,ACTION");
        ds.setUser("sa");
        db = new DatabaseManager(ds);

        try (Connection conn = db.getConnection()) {
            MigrationHarness.applyAll(conn);
        }
        policy = new ContentPolicy(db);
    }

    @AfterAll
    static void tearDown() throws SQLException {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP ALL OBJECTS");
        }
    }

    @Test
    @DisplayName("no shipped pattern contains a backslash")
    void patternsAvoidBackslashes() throws SQLException {
        // A backslash inside a SQL string means different things to different
        // databases: MariaDB reads it as an escape and H2 does not. A pattern
        // written with one therefore works on the hotel and silently matches
        // nothing in the tests, or the other way round — which is exactly how
        // this table shipped with rules that caught nobody.
        try (Connection c = db.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT label, pattern, exempt_pattern FROM habnut_content_rules")) {
            while (rs.next()) {
                assertFalse(rs.getString("pattern").contains("\\"),
                    "rule '" + rs.getString("label") + "' has a backslash in its pattern");
                String exempt = rs.getString("exempt_pattern");
                assertTrue(exempt == null || !exempt.contains("\\"),
                    "rule '" + rs.getString("label") + "' has a backslash in its exemption");
            }
        }
    }

    @Test
    @DisplayName("a fresh hotel opens with rules in force")
    void rulesAreSeeded() {
        assertTrue(policy.ruleCount() >= 10,
            "a hotel should not open with an empty policy, found " + policy.ruleCount());
    }

    // ─── what a hotel for adults leaves alone ───────────────────────────────

    @Test
    @DisplayName("swearing is not moderated")
    void swearingPasses() {
        for (String message : new String[] {
            "that furni is fucking gorgeous",
            "shit, I lost the trade",
            "what the hell happened to my room",
            "bloody hell that was close",
            "this catalogue is arse",
        }) {
            assertTrue(policy.check(message).isEmpty(), "should pass: " + message);
        }
    }

    @Test
    @DisplayName("adult conversation between adults is not moderated")
    void adultConversationPasses() {
        for (String message : new String[] {
            "you looked good in that outfit last night",
            "we're both adults, take it to whispers",
            "I'm 34 and I still play this",
            "anyone else here over 30",
            "she's hot, I'll give her that",
        }) {
            assertTrue(policy.check(message).isEmpty(), "should pass: " + message);
        }
    }

    @Test
    @DisplayName("ordinary game talk is not mistaken for a threat")
    void gameTalkPasses() {
        for (String message : new String[] {
            "that boss will kill you in one hit",
            "I'll beat you at snowstorm any day",
            "the goalkeeper killed us in that match",
            "going to batter you at battle ball",
        }) {
            assertTrue(policy.check(message).isEmpty(), "should pass: " + message);
        }
    }

    @Test
    @DisplayName("talking about your own struggles is not moderated")
    void talkingAboutYourselfPasses() {
        for (String message : new String[] {
            "I've been having a rough time lately",
            "some days I don't want to get out of bed",
            "my therapist says I'm doing better",
        }) {
            assertTrue(policy.check(message).isEmpty(), "should pass: " + message);
        }
    }

    // ─── what it stops ──────────────────────────────────────────────────────

    @Test
    @DisplayName("telling somebody to end their life is stopped")
    void selfHarmEncouragementIsStopped() {
        for (String message : new String[] {
            "kys",
            "k y s",
            "kill yourself",
            "kiiiilll yourself",
            "go die",
        }) {
            ContentPolicy.Verdict verdict = policy.check(message)
                .orElseThrow(() -> new AssertionError("should be stopped: " + message));
            assertEquals("self_harm", verdict.category());
            assertTrue(verdict.shouldMute());
        }
    }

    @Test
    @DisplayName("a threat aimed at a person is stopped")
    void threatsAreStopped() {
        ContentPolicy.Verdict verdict = policy.check("i will kill you").orElseThrow();
        assertEquals("threat", verdict.category());
        assertTrue(verdict.shouldMute());
    }

    @Test
    @DisplayName("threatening to turn up at somebody's home is stopped")
    void realWorldThreatsAreStopped() {
        assertEquals("threat",
            policy.check("im gonna find your house").orElseThrow().category());
    }

    @Test
    @DisplayName("asking for a password is stopped")
    void credentialPhishingIsStopped() {
        for (String message : new String[] {
            "what's your password",
            "send me your password and I'll give you credits",
            "give me your pw",
        }) {
            assertEquals("scam", policy.check(message)
                .orElseThrow(() -> new AssertionError("should be stopped: " + message))
                .category());
        }
    }

    @Test
    @DisplayName("threatening to publish somebody's details is stopped")
    void doxxingThreatsAreStopped() {
        assertEquals("doxxing", policy.check("I'm going to dox you").orElseThrow().category());
    }

    @Test
    @DisplayName("dehumanising a group of people is stopped")
    void hateSpeechIsStopped() {
        ContentPolicy.Verdict verdict =
            policy.check("all immigrants should die").orElseThrow();
        assertEquals("hate", verdict.category());
        assertTrue(verdict.shouldMute());
        // The most serious category should also carry the longest mute, so a
        // case nobody reaches overnight still holds.
        assertTrue(verdict.muteMinutes() >= 4320);
    }

    @Test
    @DisplayName("telling somebody they do not belong here is stopped")
    void targetedExclusionIsStopped() {
        assertEquals("hate", policy.check("go back to your country").orElseThrow().category());
    }

    @Test
    @DisplayName("sexual talk aimed at somebody who has said they are a child is stopped")
    void childSafetyIsStopped() {
        ContentPolicy.Verdict verdict =
            policy.check("im 14 and people say im sexy").orElseThrow();
        assertEquals("minor_safety", verdict.category());
        assertTrue(verdict.shouldMute());
        // Nothing else in the table is as urgent, so nothing else is muted for
        // as long while it waits for a person.
        assertEquals(10080, verdict.muteMinutes());
    }

    @Test
    @DisplayName("telling somebody to keep a conversation from their parents is stopped")
    void groomingLanguageIsStopped() {
        assertEquals("minor_safety",
            policy.check("dont tell your mum we talked").orElseThrow().category());
    }

    @Test
    @DisplayName("a phone number is flagged for a person to look at, not muted")
    void phoneNumbersAreFlagged() {
        ContentPolicy.Verdict verdict = policy.check("ring me on 07700 900123").orElseThrow();
        assertEquals("doxxing", verdict.category());
        assertFalse(verdict.shouldMute(),
            "a number in chat is worth reading, not worth silencing somebody over");
    }
}
