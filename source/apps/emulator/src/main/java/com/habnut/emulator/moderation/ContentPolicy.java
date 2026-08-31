package com.habnut.emulator.moderation;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Decides whether a message is harmful.
 *
 * The hotel is for adults and talks like it, so swearing and frank conversation
 * are not this class's business. What it looks for is the small set of things
 * that are harmful whoever is reading: hatred aimed at who somebody is, threats,
 * anything sexualising a child, publishing private details, pushing a person
 * toward self-harm, and phishing for accounts.
 *
 * Rules live in the database so a hotel can tune them without a new build. The
 * work this class does that a plain word list does not is normalisation: a rule
 * written once has to survive the ways people space out, misspell and decorate a
 * term, or it stops working the first time somebody notices it exists.
 */
public final class ContentPolicy {

    private static final Logger log = LoggerFactory.getLogger(ContentPolicy.class);

    /** What a rule says to do when it matches. */
    public enum Action { MUTE, FLAG }

    /** One rule, compiled and ready to match. */
    public record Rule(
        long id,
        String category,
        String label,
        Pattern pattern,
        Pattern exempt,
        String matchMode,
        Action action,
        int severity,
        int muteMinutes
    ) {}

    /** The outcome of checking one message. */
    public record Verdict(Rule rule, String category, Action action, int severity, int muteMinutes) {

        /** True when the message should be stopped and the player silenced. */
        public boolean shouldMute() {
            return action == Action.MUTE;
        }
    }

    private final DatabaseManager db;
    private final CopyOnWriteArrayList<Rule> rules = new CopyOnWriteArrayList<>();

    public ContentPolicy(DatabaseManager db) {
        this.db = db;
        reload();
    }

    /** Reads the rules again, for after a staff member changes one. */
    public void reload() {
        List<Rule> loaded = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, category, label, pattern, exempt_pattern, match_mode, "
                 + "action, severity, mute_minutes FROM habnut_content_rules "
                 + "WHERE enabled = 1 ORDER BY severity DESC, id")) {

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    compile(rs).ifPresent(loaded::add);
                }
            }

            rules.clear();
            rules.addAll(loaded);
            log.info("Content policy loaded {} rules", rules.size());
        } catch (SQLException e) {
            log.error("Failed to load content rules", e);
        }
    }

    /**
     * Compiles one row into a rule.
     *
     * A rule with an expression that will not compile is skipped rather than
     * taking the whole policy down with it: staff edit these, and one typo
     * should not leave the hotel unmoderated.
     */
    private Optional<Rule> compile(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        try {
            String exempt = rs.getString("exempt_pattern");
            return Optional.of(new Rule(
                id,
                rs.getString("category"),
                rs.getString("label"),
                Pattern.compile(rs.getString("pattern"), Pattern.CASE_INSENSITIVE),
                exempt == null || exempt.isBlank()
                    ? null
                    : Pattern.compile(exempt, Pattern.CASE_INSENSITIVE),
                rs.getString("match_mode"),
                "flag".equalsIgnoreCase(rs.getString("action")) ? Action.FLAG : Action.MUTE,
                rs.getInt("severity"),
                rs.getInt("mute_minutes")));
        } catch (PatternSyntaxException e) {
            log.error("Content rule {} has an expression that will not compile; skipping it", id, e);
            return Optional.empty();
        }
    }

    /**
     * Checks a message, returning the most serious rule it broke.
     *
     * Rules are ordered by severity, so the first hit is the worst one, and that
     * is what the player is told about and what a reviewer sees first.
     */
    public Optional<Verdict> check(String message) {
        if (message == null || message.isBlank()) return Optional.empty();

        Forms forms = Forms.of(message);

        for (Rule rule : rules) {
            if (!matches(rule, forms)) continue;
            return Optional.of(new Verdict(
                rule, rule.category(), rule.action(), rule.severity(), rule.muteMinutes()));
        }
        return Optional.empty();
    }

    private static boolean matches(Rule rule, Forms forms) {
        boolean hit = switch (rule.matchMode() == null ? "words" : rule.matchMode()) {
            case "condensed" -> rule.pattern().matcher(forms.plainCondensed()).find()
                             || rule.pattern().matcher(forms.decodedCondensed()).find();
            case "both" -> forms.anyMatch(rule.pattern());
            default -> rule.pattern().matcher(forms.plain()).find()
                    || rule.pattern().matcher(forms.decoded()).find();
        };

        if (!hit || rule.exempt() == null) return hit;

        // The exemption is checked against the message as written, because that
        // is where the context which makes it innocent lives.
        return !rule.exempt().matcher(forms.plain()).find();
    }

    // ─── normalisation ──────────────────────────────────────────────────────

    /**
     * The forms of a message that rules are matched against.
     *
     * Two decisions here shape how rules have to be written, so they are worth
     * naming. First, a message is kept both as typed and with digits and symbols
     * decoded back to letters: decoding alone would turn a phone number into
     * gibberish and make every rule about digits useless, while not decoding at
     * all lets "k1ll" walk straight through. Second, long runs of one character
     * collapse to a single one, so "kiiiilll" and "kill" reach a rule as the
     * same shape — which means a rule matching a word with a doubled letter must
     * write it as "kil+", because by the time the rule runs the doubling may be
     * gone.
     */
    public record Forms(String plain, String decoded,
                        String plainCondensed, String decodedCondensed) {

        static Forms of(String message) {
            String plain = normalisePlain(message);
            String decoded = decodeSubstitutions(plain);
            return new Forms(plain, decoded, condense(plain), condense(decoded));
        }

        /** True when a pattern matches any of the four forms. */
        boolean anyMatch(Pattern pattern) {
            return pattern.matcher(plain).find()
                || pattern.matcher(decoded).find()
                || pattern.matcher(plainCondensed).find()
                || pattern.matcher(decodedCondensed).find();
        }
    }

    /** Characters people substitute for letters. */
    private static final String[][] SUBSTITUTIONS = {
        {"0", "o"}, {"1", "i"}, {"3", "e"}, {"4", "a"}, {"5", "s"},
        {"7", "t"}, {"8", "b"}, {"@", "a"}, {"$", "s"}, {"!", "i"},
        {"|", "l"}, {"£", "e"},
    };

    /**
     * Puts a message into a comparable form without touching its digits.
     *
     * Accents are stripped, invisible characters removed, and long runs cut
     * down, so a rule keeps working against the usual ways of dodging it.
     * Spacing and punctuation survive, because word boundaries are what stop a
     * rule firing on the middle of an innocent word.
     */
    public static String normalisePlain(String message) {
        String text = Normalizer.normalize(message, Normalizer.Form.NFKD)
            // Combining accents, once separated from their letters.
            .replaceAll("\\p{M}+", "")
            // Zero-width and other invisible characters, used to break up words.
            .replaceAll("[\\p{Cf}\\p{Cc}]", "")
            .toLowerCase(Locale.ROOT);

        // "stoooop" and "stop" are the same word to a reader, so they are the
        // same word here.
        text = text.replaceAll("(.)\\1{2,}", "$1");

        return text.replaceAll("\\s+", " ").trim();
    }

    /** Puts digits and symbols standing in for letters back to letters. */
    public static String decodeSubstitutions(String text) {
        String decoded = text;
        for (String[] pair : SUBSTITUTIONS) {
            decoded = decoded.replace(pair[0], pair[1]);
        }
        return decoded;
    }

    /**
     * Both steps at once: the form most rules are written against.
     */
    public static String normalise(String message) {
        return decodeSubstitutions(normalisePlain(message));
    }

    /**
     * Strips every space and mark, so that a term spelt out one letter at a time
     * still reads as that term.
     *
     * This form loses word boundaries, which is why only rules that ask for it
     * are matched against it.
     */
    public static String condense(String normalised) {
        return normalised.replaceAll("[^a-z0-9]", "");
    }

    /** How many rules are in force, for the staff tools to report. */
    public int ruleCount() {
        return rules.size();
    }
}
