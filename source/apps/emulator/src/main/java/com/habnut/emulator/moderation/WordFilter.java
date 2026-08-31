package com.habnut.emulator.moderation;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public final class WordFilter {

    private static final Logger log = LoggerFactory.getLogger(WordFilter.class);

    public record FilterResult(boolean blocked, String filtered, int maxSeverity) {}

    private record Entry(Pattern pattern, int severity, String action, String replacement) {}

    private final DatabaseManager db;
    private final CopyOnWriteArrayList<Entry> entries = new CopyOnWriteArrayList<>();

    public WordFilter(DatabaseManager db) {
        this.db = db;
        reload();
    }

    public void reload() {
        List<Entry> loaded = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT word, severity, action, replacement FROM habnut_word_filter ORDER BY severity DESC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String word = Pattern.quote(rs.getString("word"));
                    Pattern p = Pattern.compile("(?i)\\b" + word + "\\b");
                    loaded.add(new Entry(p, rs.getInt("severity"), rs.getString("action"),
                        rs.getString("replacement")));
                }
            }
            entries.clear();
            entries.addAll(loaded);
            log.info("Word filter loaded {} entries", entries.size());
        } catch (SQLException e) {
            log.error("Failed to load word filter", e);
        }
    }

    public FilterResult apply(String message) {
        if (message == null || message.isBlank()) return new FilterResult(false, message, 0);
        String current = message;
        int maxSeverity = 0;
        boolean blocked = false;

        for (Entry e : entries) {
            if (!e.pattern().matcher(current).find()) continue;
            maxSeverity = Math.max(maxSeverity, e.severity());
            switch (e.action()) {
                case "block"   -> blocked = true;
                case "replace" -> {
                    String rep = e.replacement() != null ? e.replacement() : "***";
                    current = e.pattern().matcher(current).replaceAll(rep);
                }
                default -> { /* log only */ }
            }
        }
        return new FilterResult(blocked, current, maxSeverity);
    }
}
