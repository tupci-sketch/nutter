package com.habnut.emulator.db;

import java.io.IOException;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Applies the real migrations to an in-memory database.
 *
 * Tests that need the shipped schema — or the rows the migrations seed — should
 * use this rather than hand-writing tables, so that what they exercise is what
 * the hotel actually runs. H2 covers the DDL the migrations use but not every
 * MariaDB storage clause, so a few are stripped on the way in; nothing that
 * changes a column or a value is touched.
 */
public final class MigrationHarness {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    private MigrationHarness() {
    }

    /** Runs every migration, in version order, against an open connection. */
    public static void applyAll(Connection conn) throws IOException, SQLException {
        for (Path file : migrationFiles()) {
            String sql = sanitiseForH2(Files.readString(file));
            for (String stmt : splitStatements(sql)) {
                if (stmt.isBlank()) continue;
                try (Statement s = conn.createStatement()) {
                    s.execute(stmt);
                } catch (SQLException e) {
                    throw new SQLException(
                        file.getFileName() + " failed on: " + stmt + " — " + e.getMessage(), e);
                }
            }
        }
    }

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

    private static String sanitiseForH2(String sql) {
        return sql
            .replaceAll("(?i)\\s*ENGINE=\\w+", "")
            .replaceAll("(?i)\\s*DEFAULT CHARSET=\\w+", "")
            .replaceAll("(?i)\\s*COLLATE=\\w+", "")
            .replaceAll("(?i)\\s*ROW_FORMAT=\\w+", "")
            .replaceAll("(?im)^\\s*FULLTEXT KEY[^\\n]*\\n", "")
            // Index names are scoped per table in MariaDB but globally in H2.
            // Only column definitions matter here, so secondary indexes go.
            .replaceAll("(?im)^\\s*(?:UNIQUE\\s+)?KEY\\s+\\w+\\s*\\([^)]*\\),?\\s*\\n", "")
            // Removing a trailing definition leaves a dangling comma before ")".
            .replaceAll(",(\\s*)\\)", "$1)")
            // Session settings the server applies on connect; H2 has its own.
            .replaceAll("(?im)^\\s*SET\\s+(time_zone|sql_mode|NAMES|FOREIGN_KEY_CHECKS)\\b[^;]*;", "")
            .replaceAll("(?m)^\\s*--.*$", "");
    }

    /**
     * Splits on statement-terminating semicolons, then expands multi-clause
     * ALTER TABLE statements into one statement per clause. MariaDB accepts
     * several ADD/CHANGE clauses in a single ALTER; H2 accepts only one.
     */
    private static List<String> splitStatements(String sql) {
        List<String> out = new ArrayList<>();
        for (String stmt : sql.split(";\\s*\n")) {
            String trimmed = stmt.trim();
            if (trimmed.isBlank()) continue;

            Matcher alter = Pattern.compile(
                "^(ALTER TABLE\\s+\\w+)\\s+(.*)$", Pattern.DOTALL | Pattern.CASE_INSENSITIVE)
                .matcher(trimmed);
            if (!alter.find()) {
                out.add(trimmed);
                continue;
            }

            String head = alter.group(1);
            for (String clause : splitTopLevel(alter.group(2))) {
                if (!clause.isBlank()) out.add(head + " " + clause.trim());
            }
        }
        return out;
    }

    /** Splits on commas that are not inside parentheses. */
    private static List<String> splitTopLevel(String clauses) {
        List<String> parts = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < clauses.length(); i++) {
            char c = clauses.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(clauses.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(clauses.substring(start));
        return parts;
    }
}
