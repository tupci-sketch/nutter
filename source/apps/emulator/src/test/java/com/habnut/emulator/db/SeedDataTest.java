package com.habnut.emulator.db;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The content a hotel needs before anybody can do anything.
 *
 * A freshly migrated hotel has no room shapes, no furniture and an empty
 * catalogue: no room can be created or loaded, nothing can be put down, and
 * there is nothing to buy. The install step called a seeder that did not
 * exist, so this went unnoticed. These files are that content, and this test
 * applies them to the real schema so a column renamed in a migration cannot
 * quietly leave a hotel empty again.
 */
@DisplayName("Seed data")
class SeedDataTest {

    private static final Path SEEDS = Path.of("../launcher/internal/seed/sql");

    private Connection conn;

    @BeforeEach
    void migrate() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:seed_" + System.nanoTime()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE,KEY,YEAR,MONTH,DAY,HOUR,MINUTE,SECOND");
        ds.setUser("sa");
        conn = ds.getConnection();
        MigrationHarness.applyAll(conn);
    }

    /** Runs one seed file, statement by statement. */
    private void apply(String name) throws IOException, SQLException {
        Path file = SEEDS.resolve(name);
        assertTrue(Files.exists(file), name + " should ship with the launcher");

        for (String statement : split(Files.readString(file))) {
            try (Statement st = conn.createStatement()) {
                st.execute(statement);
            } catch (SQLException e) {
                throw new AssertionError(
                    name + ": statement failed:\n" + statement + "\n\n" + e.getMessage(), e);
            }
        }
    }

    /**
     * Splits a seed file into statements.
     *
     * Semicolons inside a string literal are not separators, and the room
     * heightmaps carry real line breaks, so this tracks quoting rather than
     * splitting on the character.
     */
    private static List<String> split(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean inComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);

            if (inComment) {
                if (c == '\n') inComment = false;
                else continue;
            } else if (!inString && c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                inComment = true;
                continue;
            } else if (c == '\'') {
                // Two quotes in a row are an escaped quote, not the end.
                if (inString && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    current.append("''");
                    i++;
                    continue;
                }
                inString = !inString;
            } else if (c == ';' && !inString) {
                String statement = current.toString().trim();
                if (!statement.isEmpty()) out.add(statement);
                current.setLength(0);
                continue;
            }

            current.append(c);
        }

        String last = current.toString().trim();
        if (!last.isEmpty()) out.add(last);
        return out;
    }

    private int count(String table) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // ─── base content ───────────────────────────────────────────────────────

    @Test
    @DisplayName("the base seed applies to the migrated schema")
    void baseApplies() throws Exception {
        apply("base.sql");
    }

    @Test
    @DisplayName("a seeded hotel has room shapes to build rooms from")
    void baseGivesRoomModels() throws Exception {
        apply("base.sql");
        assertTrue(count("habnut_room_models") >= 5,
            "a hotel with no room models can neither create nor load a room");
    }

    @Test
    @DisplayName("every room shape has a door on a tile you can stand on")
    void everyDoorIsWalkable() throws Exception {
        apply("base.sql");

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT id, heightmap, door_x, door_y FROM habnut_room_models")) {
            while (rs.next()) {
                String id = rs.getString("id");
                String[] rows = rs.getString("heightmap").split("\n");
                int dx = rs.getInt("door_x");
                int dy = rs.getInt("door_y");

                assertTrue(dy < rows.length, id + ": door row " + dy + " is off the map");
                assertTrue(dx < rows[dy].length(), id + ": door column " + dx + " is off the map");
                assertNotEquals('x', rows[dy].charAt(dx),
                    id + ": the door is on a blocked tile, so nobody can get in");

                int width = rows[0].length();
                for (String row : rows) {
                    assertEquals(width, row.length(), id + ": the map is not rectangular");
                }
            }
        }
    }

    @Test
    @DisplayName("a seeded hotel has furniture to put down")
    void baseGivesFurniture() throws Exception {
        apply("base.sql");
        assertTrue(count("habnut_items_base") >= 10,
            "a hotel with no furniture bases has nothing anybody can place");
    }

    @Test
    @DisplayName("a seeded hotel has something to buy")
    void baseGivesCatalogue() throws Exception {
        apply("base.sql");
        assertTrue(count("habnut_catalogue_pages") >= 5, "the catalogue has no pages");
        assertTrue(count("habnut_catalogue_offers") >= 10, "the catalogue has nothing in it");
    }

    @Test
    @DisplayName("every offer points at furniture that exists")
    void everyOfferHasAnItem() throws Exception {
        apply("base.sql");

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT COUNT(*) FROM habnut_catalogue_offers o " +
                 "LEFT JOIN habnut_items_base b ON b.id = o.base_id WHERE b.id IS NULL")) {
            rs.next();
            assertEquals(0, rs.getInt(1), "an offer that buys nothing takes the money anyway");
        }
    }

    @Test
    @DisplayName("applying the base seed twice changes nothing the second time")
    void baseIsIdempotent() throws Exception {
        apply("base.sql");
        int models = count("habnut_room_models");
        int items = count("habnut_items_base");
        int offers = count("habnut_catalogue_offers");

        apply("base.sql");

        assertEquals(models, count("habnut_room_models"));
        assertEquals(items, count("habnut_items_base"));
        assertEquals(offers, count("habnut_catalogue_offers"));
    }

    // ─── demo world ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("the demo world applies on top of the base seed")
    void demoApplies() throws Exception {
        apply("base.sql");
        apply("demo.sql");
    }

    @Test
    @DisplayName("the demo world gives you somebody to sign in as")
    void demoGivesAccounts() throws Exception {
        apply("base.sql");
        apply("demo.sql");

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT username, rank, credits FROM habnut_users WHERE username = 'tupci'")) {
            assertTrue(rs.next(), "the demo world should include tupci");
            assertEquals(7, rs.getInt("rank"), "tupci should be able to open the staff pages");
            assertTrue(rs.getInt("credits") > 0, "tupci should have something to spend");
        }

        assertTrue(count("habnut_users") >= 4, "one account alone shows nothing about other people");
    }

    @Test
    @DisplayName("the demo rooms have furniture standing in them")
    void demoRoomsAreFurnished() throws Exception {
        apply("base.sql");
        apply("demo.sql");

        assertTrue(count("habnut_rooms") >= 3, "the demo world should have rooms to walk into");
        assertTrue(count("habnut_floor_items") >= 10, "an empty room shows nothing about furniture");
        assertTrue(count("habnut_wall_items") >= 1, "wall items should be visible too");
    }

    @Test
    @DisplayName("every placed item stands on a tile inside its room")
    void everyPlacementIsInsideItsRoom() throws Exception {
        apply("base.sql");
        apply("demo.sql");

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT f.id, f.x, f.y, m.id AS model, m.heightmap " +
                 "FROM habnut_floor_items f " +
                 "JOIN habnut_rooms r ON r.id = f.room_id " +
                 "JOIN habnut_room_models m ON m.id = r.model_id")) {
            while (rs.next()) {
                String[] rows = rs.getString("heightmap").split("\n");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                long id = rs.getLong("id");

                assertTrue(y >= 0 && y < rows.length,
                    "item " + id + " is off the map in " + rs.getString("model"));
                assertTrue(x >= 0 && x < rows[y].length(),
                    "item " + id + " is off the map in " + rs.getString("model"));
                assertNotEquals('x', rows[y].charAt(x),
                    "item " + id + " stands on a blocked tile in " + rs.getString("model"));
            }
        }
    }

    @Test
    @DisplayName("both worlds have somewhere to go")
    void bothWorldsHaveRooms() throws Exception {
        apply("base.sql");
        apply("demo.sql");

        // A room belongs to one world and the navigator only shows the world
        // the player is in, so a city with no rooms of its own is a city with
        // nowhere to go — which is what `dev up --rp` used to come up as.
        for (String world : List.of("classic", "nutropolis")) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM habnut_rooms WHERE world_id = '" + world + "'")) {
                rs.next();
                assertTrue(rs.getInt(1) >= 3,
                    world + " has fewer than three rooms, so the navigator looks broken there");
            }
        }
    }

    @Test
    @DisplayName("every room in either world has furniture in it")
    void everyWorldsRoomsAreFurnished() throws Exception {
        apply("base.sql");
        apply("demo.sql");

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT r.name FROM habnut_rooms r " +
                 "LEFT JOIN habnut_floor_items f ON f.room_id = r.id " +
                 "GROUP BY r.id, r.name HAVING COUNT(f.id) = 0")) {
            List<String> empty = new ArrayList<>();
            while (rs.next()) empty.add(rs.getString("name"));
            assertTrue(empty.isEmpty(),
                "these demo rooms have nothing in them: " + String.join(", ", empty));
        }
    }

    @Test
    @DisplayName("the demo world gives you friends, inventory and a forum post")
    void demoGivesTheRest() throws Exception {
        apply("base.sql");
        apply("demo.sql");

        assertTrue(count("habnut_friends") >= 2, "the friends list should not be empty");
        assertTrue(count("habnut_items_inventory") >= 3, "the inventory should not be empty");
        assertTrue(count("habnut_forum_threads") >= 1, "the forum should not be empty");
        assertTrue(count("habnut_forum_posts") >= 1, "a thread with no posts reads as broken");
    }

    @Test
    @DisplayName("applying the demo world twice changes nothing the second time")
    void demoIsIdempotent() throws Exception {
        apply("base.sql");
        apply("demo.sql");
        int users = count("habnut_users");
        int rooms = count("habnut_rooms");
        int items = count("habnut_floor_items");

        apply("demo.sql");

        assertEquals(users, count("habnut_users"));
        assertEquals(rooms, count("habnut_rooms"));
        assertEquals(items, count("habnut_floor_items"));
    }

    // ─── portability ────────────────────────────────────────────────────────

    @Test
    @DisplayName("no seeded string literal contains a backslash")
    void noBackslashes() throws IOException {
        for (String name : List.of("base.sql", "demo.sql")) {
            String body = Files.readString(SEEDS.resolve(name));
            assertFalse(body.contains("\\"),
                name + " contains a backslash. MariaDB unescapes backslash sequences in a "
                + "string literal and H2 does not, so the same file would seed two different "
                + "things depending on where it ran.");
        }
    }
}
