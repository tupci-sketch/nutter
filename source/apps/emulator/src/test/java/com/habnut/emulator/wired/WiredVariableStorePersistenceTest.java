package com.habnut.emulator.wired;

import com.habnut.emulator.db.DatabaseManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence for wired variables.
 *
 * The conformance suite exercises wired semantics in memory. This covers the
 * other half: that a variable survives a restart, that each scope is stored
 * under its own key, and that a database failure degrades to a usable value
 * rather than propagating out into a room's execution.
 */
@DisplayName("Wired variable persistence")
class WiredVariableStorePersistenceTest {

    private static final long ROOM = 100L;
    private static final long USER = 7L;

    private DatabaseManager db;
    private WiredVariableStore store;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:wiredvars_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        db = new DatabaseManager(ds);
        store = new WiredVariableStore(db);

        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE habnut_wired_variables (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    scope VARCHAR(16) NOT NULL,
                    room_id BIGINT NOT NULL DEFAULT 0,
                    user_id BIGINT NOT NULL DEFAULT 0,
                    var_name VARCHAR(64) NOT NULL,
                    var_type VARCHAR(16) NOT NULL,
                    num_value DOUBLE NOT NULL DEFAULT 0,
                    text_value VARCHAR(512),
                    bool_value BOOLEAN NOT NULL DEFAULT FALSE,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uq_var (scope, room_id, user_id, var_name)
                )""");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP ALL OBJECTS");
        }
    }

    /** A fresh store shares the database but not the in-memory cache. */
    private WiredVariableStore afterRestart() {
        return new WiredVariableStore(db);
    }

    @Test
    @DisplayName("a number survives a restart")
    void numberPersists() {
        store.set(WiredContext.Scope.ROOM, ROOM, USER, "score", WiredValue.ofNumber(1234));

        assertEquals(1234, afterRestart().get(WiredContext.Scope.ROOM, ROOM, USER, "score").asNumber());
    }

    @Test
    @DisplayName("text and bool keep their type across a restart")
    void typesSurviveARestart() {
        store.set(WiredContext.Scope.ROOM, ROOM, USER, "label", WiredValue.ofText("hello"));
        store.set(WiredContext.Scope.ROOM, ROOM, USER, "flag", WiredValue.TRUE);

        WiredVariableStore reloaded = afterRestart();
        WiredValue label = reloaded.get(WiredContext.Scope.ROOM, ROOM, USER, "label");
        WiredValue flag  = reloaded.get(WiredContext.Scope.ROOM, ROOM, USER, "flag");

        assertEquals(WiredValue.Type.TEXT, label.getType());
        assertEquals("hello", label.asText());
        assertEquals(WiredValue.Type.BOOL, flag.getType());
        assertTrue(flag.asBool());
    }

    @Test
    @DisplayName("writing the same variable twice updates rather than duplicates")
    void writesUpsert() throws SQLException {
        store.set(WiredContext.Scope.ROOM, ROOM, USER, "n", WiredValue.ofNumber(1));
        store.set(WiredContext.Scope.ROOM, ROOM, USER, "n", WiredValue.ofNumber(2));
        store.set(WiredContext.Scope.ROOM, ROOM, USER, "n", WiredValue.ofNumber(3));

        try (Connection c = db.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM habnut_wired_variables")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "one row per variable, not one per write");
        }
        assertEquals(3, afterRestart().get(WiredContext.Scope.ROOM, ROOM, USER, "n").asNumber());
    }

    @Test
    @DisplayName("each scope persists under its own key")
    void scopesPersistSeparately() {
        store.set(WiredContext.Scope.ROOM,   ROOM, USER, "v", WiredValue.ofNumber(1));
        store.set(WiredContext.Scope.USER,   ROOM, USER, "v", WiredValue.ofNumber(2));
        store.set(WiredContext.Scope.GLOBAL, ROOM, USER, "v", WiredValue.ofNumber(3));

        WiredVariableStore reloaded = afterRestart();
        assertEquals(1, reloaded.get(WiredContext.Scope.ROOM,   ROOM, USER, "v").asNumber());
        assertEquals(2, reloaded.get(WiredContext.Scope.USER,   ROOM, USER, "v").asNumber());
        assertEquals(3, reloaded.get(WiredContext.Scope.GLOBAL, ROOM, USER, "v").asNumber());
    }

    @Test
    @DisplayName("a room variable in one room does not load into another")
    void roomScopeStaysSeparateOnDisk() {
        store.set(WiredContext.Scope.ROOM, ROOM, USER, "v", WiredValue.ofNumber(50));

        assertEquals(0, afterRestart().get(WiredContext.Scope.ROOM, 999L, USER, "v").asNumber());
    }

    @Test
    @DisplayName("a user variable follows the user between rooms")
    void userScopeIgnoresRoom() {
        store.set(WiredContext.Scope.USER, ROOM, USER, "coins", WiredValue.ofNumber(80));

        assertEquals(80, afterRestart().get(WiredContext.Scope.USER, 999L, USER, "coins").asNumber());
    }

    @Test
    @DisplayName("an unset variable reads as zero from the database")
    void unsetReadsZeroFromDatabase() {
        assertEquals(0, afterRestart().get(WiredContext.Scope.ROOM, ROOM, USER, "never_written").asNumber());
    }

    @Test
    @DisplayName("eviction drops the cache but leaves the stored value")
    void evictionKeepsTheStoredValue() {
        store.set(WiredContext.Scope.ROOM, ROOM, USER, "v", WiredValue.ofNumber(9));

        store.evictRoom(ROOM);

        assertEquals(9, store.get(WiredContext.Scope.ROOM, ROOM, USER, "v").asNumber(),
            "evicting the cache should re-read from the database, not lose the value");
    }

    @Test
    @DisplayName("a database failure degrades to zero instead of propagating")
    void databaseFailureIsContained() throws SQLException {
        store.set(WiredContext.Scope.ROOM, ROOM, USER, "v", WiredValue.ofNumber(5));

        // Remove the table underneath the store, as an outage would.
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE habnut_wired_variables");
        }

        WiredVariableStore reloaded = afterRestart();
        assertDoesNotThrow(() -> reloaded.get(WiredContext.Scope.ROOM, ROOM, USER, "v"),
            "a failed read must not escape into room execution");
        assertEquals(0, reloaded.get(WiredContext.Scope.ROOM, ROOM, USER, "v").asNumber());

        assertDoesNotThrow(() -> reloaded.set(WiredContext.Scope.ROOM, ROOM, USER, "v", WiredValue.ONE),
            "a failed write must not escape either");
    }
}
