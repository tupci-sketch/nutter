package com.habnut.emulator.db;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the schema against drift from the services that read it.
 *
 * Every repository catches SQLException, logs it and returns an empty result.
 * That is the right behaviour at runtime, but it means a column renamed in a
 * migration — or never created — produces an empty navigator and an empty
 * inventory rather than a crash. This test reads the column names out of the
 * migrations and compares them against the columns the service SQL selects, so
 * that drift fails the build instead of emptying the hotel.
 */
@DisplayName("Migration schema")
class MigrationSchemaTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final Path SOURCES    = Path.of("src/main/java");

    /** SQL keywords and functions that can follow an alias-like token. */
    private static final Set<String> SQL_WORDS = Set.of(
        "select", "from", "where", "and", "or", "join", "on", "as", "set", "values",
        "insert", "into", "update", "delete", "order", "by", "group", "having", "limit",
        "desc", "asc", "left", "inner", "outer", "count", "sum", "max", "min", "avg",
        "distinct", "null", "not", "is", "in", "like", "between", "case", "when", "then",
        "else", "end", "duplicate", "key", "ignore", "now", "coalesce", "greatest", "least",
        "if", "exists", "union", "all", "for", "share", "lock", "mode", "interval",
        "date_sub", "date_add", "ifnull", "concat", "json_array", "json_object");

    // ─── tests ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("every migration applies cleanly in order")
    void migrationsApply() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        // NON_KEYWORDS releases identifiers H2 reserves but MariaDB does not,
        // so a column legitimately named "value" is not a syntax error here.
        ds.setURL("jdbc:h2:mem:migrations_" + System.nanoTime()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE,KEY,YEAR,MONTH,DAY,HOUR,MINUTE,SECOND");
        ds.setUser("sa");

        try (Connection conn = ds.getConnection()) {
            assertDoesNotThrow(() -> MigrationHarness.applyAll(conn),
                "every migration should apply cleanly, in order");
        }
    }

    @Test
    @DisplayName("no service selects a column the schema does not define")
    void noColumnDrift() throws IOException {
        Map<String, Set<String>> schema = parseSchema();
        List<String> problems = new ArrayList<>();

        for (Path java : javaSources()) {
            String sqlText = extractStringLiterals(Files.readString(java));

            Matcher tableRef = Pattern.compile(
                "\\b(?:FROM|JOIN)\\s+(habnut_\\w+)\\s+(\\w+)", Pattern.CASE_INSENSITIVE)
                .matcher(sqlText);

            while (tableRef.find()) {
                String table = tableRef.group(1).toLowerCase();
                String alias = tableRef.group(2).toLowerCase();
                if (SQL_WORDS.contains(alias)) continue;

                Set<String> columns = schema.get(table);
                if (columns == null) {
                    problems.add(java.getFileName() + ": unknown table " + table);
                    continue;
                }

                Matcher colRef = Pattern.compile("\\b" + Pattern.quote(alias) + "\\.(\\w+)")
                    .matcher(sqlText);
                while (colRef.find()) {
                    String column = colRef.group(1).toLowerCase();
                    if (column.equals("*") || SQL_WORDS.contains(column)) continue;
                    if (!columns.contains(column)) {
                        problems.add(java.getFileName() + ": " + table + " has no column '"
                            + column + "' (referenced as " + alias + "." + column + ")");
                    }
                }
            }
        }

        List<String> distinct = problems.stream().distinct().sorted().toList();
        assertTrue(distinct.isEmpty(),
            "service SQL references columns the schema does not define:\n  "
                + String.join("\n  ", distinct));
    }

    /**
     * The two tests above read the SQL. This one reads what the mapping code
     * asks the result set for, which is a separate thing and drifted on its own:
     * UserRepository selected "figure, rank" correctly and then asked for
     * "figure_string" and "rank_id", so every user lookup threw and login
     * resolved nobody. FurniBaseRepository did the same with four labels and
     * preloaded no furniture at all. Both passed the SQL tests, because the SQL
     * was right — only the reader was wrong.
     *
     * A label is accepted if the file's own SQL names it (so an alias, whether
     * written with AS or not, is fine) or if it is a column of some habnut_
     * table that file queries (so SELECT * is fine). Anything else is a label
     * no result set will carry.
     */
    @Test
    @DisplayName("no service reads a result label nothing can produce")
    void noResultLabelDrift() throws IOException {
        Map<String, Set<String>> schema = parseSchema();
        List<String> problems = new ArrayList<>();

        Pattern getter = Pattern.compile("\\brs\\.get[A-Za-z]+\\(\\s*\"([a-z0-9_]+)\"");
        Pattern tableRef = Pattern.compile(
            "\\b(?:FROM|JOIN|INTO|UPDATE)\\s+(habnut_\\w+)", Pattern.CASE_INSENSITIVE);

        for (Path java : javaSources()) {
            String source = Files.readString(java);
            Matcher labels = getter.matcher(source);
            if (!labels.find()) continue;

            // The getter's own argument is a string literal too, so it has to
            // come out before the SQL is read — otherwise every label proves
            // itself and the test can never fail.
            String sqlText = extractStringLiterals(
                getter.matcher(source).replaceAll(" "));

            // every word the file's SQL mentions: covers aliases of both forms
            Set<String> sqlWords = new HashSet<>();
            Matcher word = Pattern.compile("[a-z0-9_]+").matcher(sqlText.toLowerCase());
            while (word.find()) sqlWords.add(word.group());

            // plus the columns of every table it touches, for SELECT *
            Set<String> reachable = new HashSet<>();
            Matcher t = tableRef.matcher(sqlText);
            while (t.find()) {
                Set<String> cols = schema.get(t.group(1).toLowerCase());
                if (cols != null) reachable.addAll(cols);
            }

            labels.reset();
            while (labels.find()) {
                String label = labels.group(1).toLowerCase();
                if (sqlWords.contains(label) || reachable.contains(label)) continue;
                problems.add(java.getFileName() + ": reads '" + label
                    + "' but no query in the file produces that label");
            }
        }

        List<String> distinct = problems.stream().distinct().sorted().toList();
        assertTrue(distinct.isEmpty(),
            "service code reads result labels no query produces:\n  "
                + String.join("\n  ", distinct));
    }

    @Test
    @DisplayName("no service selects a bare column the schema does not define")
    void noBareSelectDrift() throws IOException {
        Map<String, Set<String>> schema = parseSchema();
        List<String> problems = new ArrayList<>();

        // A query against one table has no need of an alias, so its columns are
        // named bare — and the aliased check above never looked at them. That
        // blind spot covered the user lookup every login goes through, which
        // had been selecting four columns that do not exist.
        Pattern select = Pattern.compile(
            "SELECT\\s+(.+?)\\s+FROM\\s+(habnut_\\w+)\\s*(?:WHERE|ORDER|GROUP|LIMIT|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        for (Path java : javaSources()) {
            String sqlText = extractStringLiterals(Files.readString(java));

            Matcher m = select.matcher(sqlText);
            while (m.find()) {
                String columns = m.group(1);
                String table = m.group(2).toLowerCase();

                // Anything qualified or joined is the aliased check's business.
                if (columns.contains(".") || columns.toUpperCase().contains("JOIN")) continue;

                Set<String> defined = schema.get(table);
                if (defined == null) {
                    problems.add(java.getFileName() + ": unknown table " + table);
                    continue;
                }

                for (String named : columns.split(",")) {
                    String column = named.trim().toLowerCase().replace("`", "");
                    // Counts, expressions, literals and aliased results are not
                    // plain column references and carry their own names.
                    if (column.isEmpty() || column.contains("(") || column.contains("*")
                        || column.contains(" ") || SQL_WORDS.contains(column)
                        // "SELECT 1 FROM ... " asks whether a row exists; the 1
                        // is a literal, not a column.
                        || column.chars().allMatch(Character::isDigit)) {
                        continue;
                    }
                    if (!defined.contains(column)) {
                        problems.add(java.getFileName() + ": " + table
                            + " has no column '" + column + "' (selected)");
                    }
                }
            }
        }

        List<String> distinct = problems.stream().distinct().sorted().toList();
        assertTrue(distinct.isEmpty(),
            "service SQL selects columns the schema does not define:\n  "
                + String.join("\n  ", distinct));
    }

    @Test
    @DisplayName("no service inserts into a column the schema does not define")
    void noInsertDrift() throws IOException {
        Map<String, Set<String>> schema = parseSchema();
        List<String> problems = new ArrayList<>();

        // An INSERT names its columns in a bare list rather than through an
        // alias, so the check above never looked at one. Two tables had been
        // written to for months with column names that did not exist; every
        // repository catches its own exception and carries on, so the only
        // symptom was a leaderboard that stayed empty forever.
        Pattern insert = Pattern.compile(
            "INSERT\\s+(?:IGNORE\\s+)?INTO\\s+(habnut_\\w+)\\s*\\(([^)]*)\\)",
            Pattern.CASE_INSENSITIVE);

        for (Path java : javaSources()) {
            String sqlText = extractStringLiterals(Files.readString(java));

            Matcher m = insert.matcher(sqlText);
            while (m.find()) {
                String table = m.group(1).toLowerCase();
                Set<String> columns = schema.get(table);
                if (columns == null) {
                    problems.add(java.getFileName() + ": unknown table " + table);
                    continue;
                }

                for (String named : m.group(2).split(",")) {
                    String column = named.trim().toLowerCase().replace("`", "");
                    if (column.isEmpty() || SQL_WORDS.contains(column)) continue;
                    if (!columns.contains(column)) {
                        problems.add(java.getFileName() + ": " + table
                            + " has no column '" + column + "' (inserted into)");
                    }
                }
            }
        }

        List<String> distinct = problems.stream().distinct().sorted().toList();
        assertTrue(distinct.isEmpty(),
            "service SQL inserts into columns the schema does not define:\n  "
                + String.join("\n  ", distinct));
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    /** Migration files ordered by their numeric version, so V10 follows V9. */
    private static List<Path> migrationFiles() throws IOException {
        Pattern version = Pattern.compile("V(\\d+)__");
        try (var stream = Files.list(MIGRATIONS)) {
            return stream
                .filter(p -> p.toString().endsWith(".sql"))
                .sorted(Comparator.comparingInt(p -> {
                    Matcher m = version.matcher(p.getFileName().toString());
                    return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
                }))
                .collect(Collectors.toList());
        }
    }

    private static List<Path> javaSources() throws IOException {
        try (var stream = Files.walk(SOURCES)) {
            return stream.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }

    /**
     * Builds table → column names by replaying CREATE TABLE, RENAME TO, and the
     * ADD/CHANGE COLUMN clauses of every ALTER TABLE, in migration order.
     */
    private static Map<String, Set<String>> parseSchema() throws IOException {
        Map<String, Set<String>> schema = new HashMap<>();

        Pattern create = Pattern.compile("CREATE TABLE (\\w+)\\s*\\((.*?)\\n\\)", Pattern.DOTALL);
        Pattern alter  = Pattern.compile("ALTER TABLE (\\w+)(.*?);", Pattern.DOTALL);
        Pattern column = Pattern.compile(
            "^(\\w+)\\s+(INT|BIGINT|SMALLINT|TINYINT|VARCHAR|CHAR|TEXT|JSON|ENUM|DATETIME|"
            + "TIMESTAMP|DECIMAL|DOUBLE|FLOAT|BLOB|DATE|MEDIUMTEXT|LONGTEXT|BOOLEAN)",
            Pattern.CASE_INSENSITIVE);
        Pattern addCol    = Pattern.compile("ADD COLUMN\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Pattern changeCol = Pattern.compile("CHANGE COLUMN\\s+(\\w+)\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Pattern renameTo  = Pattern.compile("^\\s*RENAME TO\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        // `RENAME COLUMN a TO b` renames one column, the same as CHANGE COLUMN
        // does, and is what the migrations use wherever a foreign key depends
        // on the column — MariaDB refuses to rename one of those inside a
        // combined ALTER. Missing it here made every such column read as
        // undefined and the drift guards fire on a schema that was correct.
        Pattern renameCol = Pattern.compile(
            "RENAME COLUMN\\s+(\\w+)\\s+TO\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

        for (Path file : migrationFiles()) {
            String sql = Files.readString(file);

            Matcher c = create.matcher(sql);
            while (c.find()) {
                Set<String> cols = new HashSet<>();
                for (String line : c.group(2).split("\n")) {
                    Matcher col = column.matcher(line.trim());
                    if (col.find()) cols.add(col.group(1).toLowerCase());
                }
                schema.put(c.group(1).toLowerCase(), cols);
            }

            Matcher a = alter.matcher(sql);
            while (a.find()) {
                String table = a.group(1).toLowerCase();

                // A renamed table keeps its columns under the new name, and the
                // old name stops existing — so SQL still using it is drift.
                Matcher rename = renameTo.matcher(a.group(2));
                if (rename.find()) {
                    Set<String> moved = schema.remove(table);
                    if (moved != null) schema.put(rename.group(1).toLowerCase(), moved);
                    continue;
                }

                Set<String> cols = schema.get(table);
                if (cols == null) continue;
                Matcher add = addCol.matcher(a.group(2));
                while (add.find()) cols.add(add.group(1).toLowerCase());
                Matcher chg = changeCol.matcher(a.group(2));
                while (chg.find()) {
                    cols.remove(chg.group(1).toLowerCase());
                    cols.add(chg.group(2).toLowerCase());
                }
                Matcher ren = renameCol.matcher(a.group(2));
                while (ren.find()) {
                    cols.remove(ren.group(1).toLowerCase());
                    cols.add(ren.group(2).toLowerCase());
                }
            }
        }
        return schema;
    }

    /** Concatenates every Java string literal so multi-line SQL reads as one. */
    private static String extractStringLiterals(String source) {
        StringBuilder sb = new StringBuilder();
        Matcher m = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(source);
        while (m.find()) sb.append(m.group(1)).append(' ');
        return sb.toString();
    }

    /** Removes MariaDB-only clauses H2 does not accept. */
}
