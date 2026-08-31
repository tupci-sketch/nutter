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
