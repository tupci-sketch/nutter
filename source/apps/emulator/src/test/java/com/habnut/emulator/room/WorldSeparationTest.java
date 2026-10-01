package com.habnut.emulator.room;

import com.habnut.emulator.db.DatabaseManager;
import com.habnut.emulator.db.MigrationHarness;
import com.habnut.emulator.economy.CatalogueService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The hotel and the roleplay city are two worlds, not one wearing two names.
 *
 * `world_id` has been on the rooms, the room categories and the catalogue
 * pages since the first migration, and every service ignored it. A player in
 * the hotel was shown the city's rooms in their navigator and could walk into
 * one; they were offered the city's furniture and could buy it. Nothing failed
 * — the separation the whole project is built around simply was not there.
 */
@DisplayName("World separation")
class WorldSeparationTest {

    private JdbcDataSource ds;
    private DatabaseManager db;

    @BeforeEach
    void migrate() throws Exception {
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:worlds_" + System.nanoTime()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE,KEY,YEAR,MONTH,DAY,HOUR,MINUTE,SECOND");
        ds.setUser("sa");

        try (Connection conn = ds.getConnection()) {
            MigrationHarness.applyAll(conn);
            seed(conn);
        }
        db = new DatabaseManager(ds);
    }

    /** Two players, two worlds, a room and a catalogue page in each. */
    private void seed(Connection conn) throws SQLException {
        exec(conn, "INSERT INTO habnut_users (id, username, email, password_hash, figure) "
            + "VALUES (1, 'tupci', 'tupci@example.test', 'x', 'hd-180-1')");

        exec(conn, "INSERT INTO habnut_room_models (id, heightmap, door_x, door_y, door_rotation) "
            + "VALUES ('model_a', '000\n000\n000', 0, 0, 2)");

        exec(conn, "INSERT INTO habnut_rooms "
            + "(id, name, description, owner_id, model_id, world_id, access_type, max_visitors) "
            + "VALUES (1, 'Hotel Lobby', '', 1, 'model_a', 'classic', 0, 25)");
        exec(conn, "INSERT INTO habnut_rooms "
            + "(id, name, description, owner_id, model_id, world_id, access_type, max_visitors) "
            + "VALUES (2, 'City Hall', '', 1, 'model_a', 'nutropolis', 0, 25)");

        exec(conn, "INSERT INTO habnut_catalogue_pages "
            + "(id, name, caption, visible, world_id, layout, min_rank) "
            + "VALUES (1, 'Hotel Furniture', '', 1, 'classic', 'default_3x3', 1)");
        exec(conn, "INSERT INTO habnut_catalogue_pages "
            + "(id, name, caption, visible, world_id, layout, min_rank) "
            + "VALUES (2, 'City Supplies', '', 1, 'nutropolis', 'default_3x3', 1)");
        exec(conn, "INSERT INTO habnut_catalogue_pages "
            + "(id, name, caption, visible, world_id, layout, min_rank) "
            + "VALUES (3, 'Everywhere', '', 1, 'both', 'default_3x3', 1)");
    }

    private static void exec(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private static List<String> names(List<RoomSettings> rooms) {
        return rooms.stream().map(RoomSettings::name).toList();
    }

    // ─── rooms ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the navigator shows only the rooms of the world you are in")
    void popularIsPerWorld() {
        RoomRepository repo = new RoomRepository(db);

        assertEquals(List.of("Hotel Lobby"), names(repo.getPopular("classic", 40)),
            "a player in the hotel should not be shown the city's rooms");
        assertEquals(List.of("City Hall"), names(repo.getPopular("nutropolis", 40)),
            "a player in the city should not be shown the hotel's rooms");
    }

    @Test
    @DisplayName("searching does not reach into the other world")
    void searchIsPerWorld() {
        RoomRepository repo = new RoomRepository(db);

        assertTrue(names(repo.searchPublic("", "classic", 40)).contains("Hotel Lobby"));
        assertFalse(names(repo.searchPublic("", "classic", 40)).contains("City Hall"));
        assertFalse(names(repo.searchPublic("City", "classic", 40)).contains("City Hall"),
            "searching the hotel by name should not find a room in the city");
    }

    @Test
    @DisplayName("your own rooms list is the ones in this world")
    void ownRoomsArePerWorld() {
        RoomRepository repo = new RoomRepository(db);

        assertEquals(List.of("Hotel Lobby"), names(repo.findByOwner(1, "classic")));
        assertEquals(List.of("City Hall"), names(repo.findByOwner(1, "nutropolis")));
    }

    @Test
    @DisplayName("a room carries the world it is in, so entry can be refused")
    void roomKnowsItsWorld() {
        RoomRepository repo = new RoomRepository(db);

        assertEquals("classic", repo.findById(1).orElseThrow().worldId());
        assertEquals("nutropolis", repo.findById(2).orElseThrow().worldId());
    }

    // ─── catalogue ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("the catalogue shows this world's pages and the shared ones")
    void cataloguePagesArePerWorld() {
        CatalogueService catalogue = new CatalogueService(db, null, null);

        List<String> hotel = catalogue.getPages(1, "classic").stream()
            .map(CatalogueService.CatPage::name).toList();
        assertTrue(hotel.contains("Hotel Furniture"));
        assertTrue(hotel.contains("Everywhere"), "a page marked 'both' belongs to both");
        assertFalse(hotel.contains("City Supplies"),
            "a hotel guest was being offered the city's furniture");

        List<String> city = catalogue.getPages(1, "nutropolis").stream()
            .map(CatalogueService.CatPage::name).toList();
        assertTrue(city.contains("City Supplies"));
        assertTrue(city.contains("Everywhere"));
        assertFalse(city.contains("Hotel Furniture"));
    }

    @Test
    @DisplayName("a page from the other world cannot be opened by its id")
    void aPageFromTheOtherWorldIsNotFound() {
        CatalogueService catalogue = new CatalogueService(db, null, null);

        assertNotNull(catalogue.getPage(1, 1, "classic"));
        assertNull(catalogue.getPage(2, 1, "classic"),
            "asking for the city's page by id while in the hotel should find nothing");
        assertNotNull(catalogue.getPage(3, 1, "classic"), "a shared page is open to both");
        assertNotNull(catalogue.getPage(3, 1, "nutropolis"));
    }

    @Test
    @DisplayName("buying from the other world's page is refused")
    void buyingAcrossWorldsIsRefused() throws Exception {
        try (Connection conn = ds.getConnection()) {
            exec(conn, "INSERT INTO habnut_items_base (id, sprite_id, name, type) "
                + "VALUES (1, 'chair', 'Chair', 'floor')");
            exec(conn, "INSERT INTO habnut_catalogue_offers "
                + "(id, page_id, base_id, name, items_json, credits_price, is_visible) "
                + "VALUES (1, 2, 1, 'City Chair', '[]', 10, 1)");
        }

        CatalogueService catalogue = new CatalogueService(db, null, null);

        // The offer is on the city's page; a hotel guest holding its id must
        // still be refused, because an item id is only a number.
        CatalogueService.PurchaseResult result = catalogue.purchase(1, 1, 7, "classic");
        assertFalse(result.success(), "a hotel guest bought from the city's catalogue");
        assertEquals("Access denied", result.error());
    }
}
