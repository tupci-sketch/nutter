package com.habnut.emulator.rp;

import com.habnut.emulator.db.DatabaseManager;
import com.habnut.emulator.db.MigrationHarness;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Money a faction holds together.
 *
 * Territory used to produce an hourly income that went nowhere — a number the
 * server could work out and nothing could receive. These cover both halves of
 * fixing that: the ledger itself, and territory actually paying into it without
 * a restart letting somebody collect the same hour twice.
 */
@DisplayName("Faction treasury")
class RpTreasuryServiceTest {

    private static final int FIRM = 1;
    private static final int RIVALS = 2;

    private DatabaseManager db;
    private RpTreasuryService treasury;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:treasury_" + System.nanoTime()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE,KEY,YEAR,MONTH,DAY,HOUR,MINUTE,SECOND,ACTION");
        ds.setUser("sa");
        db = new DatabaseManager(ds);

        try (Connection conn = db.getConnection()) {
            MigrationHarness.applyAll(conn);
            try (Statement s = conn.createStatement()) {
                s.execute("INSERT INTO habnut_rp_factions (id, name, tag) VALUES "
                    + "(" + FIRM + ", 'The Firm', 'criminal'), "
                    + "(" + RIVALS + ", 'The Others', 'criminal')");
            }
        }
        treasury = new RpTreasuryService(db);
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP ALL OBJECTS");
        }
    }

    // ─── the ledger ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a new faction holds nothing")
    void startsEmpty() {
        assertEquals(0, treasury.balanceOf(FIRM));
    }

    @Test
    @DisplayName("money in and money out land on the balance")
    void creditsAndDebits() {
        assertTrue(treasury.credit(FIRM, 500, RpTreasuryService.Kind.HEIST, "A job", null).ok());
        assertEquals(500, treasury.balanceOf(FIRM));

        assertTrue(treasury.debit(FIRM, 200, RpTreasuryService.Kind.PAYROLL, "Wages", null).ok());
        assertEquals(300, treasury.balanceOf(FIRM));
    }

    @Test
    @DisplayName("the ledger sums to the balance")
    void ledgerSumsToBalance() {
        treasury.credit(FIRM, 500, RpTreasuryService.Kind.HEIST, "A job", null);
        treasury.credit(FIRM, 120, RpTreasuryService.Kind.TURF_INCOME, "Territory", null);
        treasury.debit(FIRM, 80, RpTreasuryService.Kind.PAYROLL, "Wages", null);

        // Members will argue about where the money went; the answer should not
        // depend on somebody's memory.
        long summed = treasury.history(FIRM, 100).stream()
            .mapToLong(RpTreasuryService.Entry::amount).sum();
        assertEquals(treasury.balanceOf(FIRM), summed);
    }

    @Test
    @DisplayName("a withdrawal is recorded as a negative amount")
    void withdrawalsAreSigned() {
        treasury.credit(FIRM, 500, RpTreasuryService.Kind.HEIST, "A job", null);
        treasury.debit(FIRM, 200, RpTreasuryService.Kind.WITHDRAWAL, "Out", 7L);

        RpTreasuryService.Entry latest = treasury.history(FIRM, 1).get(0);
        assertEquals(-200, latest.amount());
        assertEquals(300, latest.balanceAfter());
        assertEquals(7L, latest.actorCharacterId());
    }

    @Test
    @DisplayName("a faction cannot spend money it does not have")
    void cannotOverspend() {
        treasury.credit(FIRM, 100, RpTreasuryService.Kind.HEIST, "A job", null);

        RpTreasuryService.Result result = treasury.debit(
            FIRM, 500, RpTreasuryService.Kind.WITHDRAWAL, "Too much", null);

        assertFalse(result.ok());
        assertEquals("insufficient_funds", result.reason());
        assertEquals(100, treasury.balanceOf(FIRM));
    }

    @Test
    @DisplayName("a fine larger than the treasury empties it rather than creating a debt")
    void finesTakeWhatIsThere() {
        treasury.credit(FIRM, 100, RpTreasuryService.Kind.HEIST, "A job", null);

        // A faction in debt with no way to repay is a faction that can never do
        // anything again, which is not a punishment, it is an ending.
        RpTreasuryService.Result result = treasury.debit(
            FIRM, 5000, RpTreasuryService.Kind.FINE, "Court order", null);

        assertTrue(result.ok());
        assertEquals(0, treasury.balanceOf(FIRM));
        assertEquals(-100, treasury.history(FIRM, 1).get(0).amount(),
            "the ledger should record what was actually taken");
    }

    @Test
    @DisplayName("a movement of nothing is refused")
    void zeroIsNotAMovement() {
        assertFalse(treasury.credit(FIRM, 0, RpTreasuryService.Kind.DEPOSIT, "", null).ok());
        assertFalse(treasury.debit(FIRM, -50, RpTreasuryService.Kind.WITHDRAWAL, "", null).ok());
    }

    @Test
    @DisplayName("factions keep their money apart")
    void factionsAreSeparate() {
        treasury.credit(FIRM, 500, RpTreasuryService.Kind.HEIST, "A job", null);

        assertEquals(500, treasury.balanceOf(FIRM));
        assertEquals(0, treasury.balanceOf(RIVALS));
    }

    @Test
    @DisplayName("a faction that does not exist takes no money")
    void unknownFactionsAreRefused() {
        RpTreasuryService.Result result = treasury.credit(
            999, 100, RpTreasuryService.Kind.HEIST, "A job", null);

        assertFalse(result.ok());
        assertEquals("no_such_faction", result.reason());
    }

    @Test
    @DisplayName("history is newest first and bounded")
    void historyIsBounded() {
        for (int i = 1; i <= 10; i++) {
            treasury.credit(FIRM, i, RpTreasuryService.Kind.DEPOSIT, "Deposit " + i, null);
        }

        List<RpTreasuryService.Entry> recent = treasury.history(FIRM, 3);
        assertEquals(3, recent.size());
        assertEquals(10, recent.get(0).amount(), "the newest entry should come first");
    }

    // ─── territory income ───────────────────────────────────────────────────

    private int turf(String code, int factionId, int incomePerHour) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_rp_turfs (code, name, income_per_hour, owner_faction_id) "
                 + "VALUES (?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code);
            ps.setString(2, code);
            ps.setInt(3, incomePerHour);
            ps.setInt(4, factionId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    @Test
    @DisplayName("territory pays the faction that holds it")
    void territoryPays() throws SQLException {
        turf("docks", FIRM, 150);
        turf("market", FIRM, 100);
        turf("station", RIVALS, 75);

        assertEquals(325, treasury.payTerritoryIncome());
        assertEquals(250, treasury.balanceOf(FIRM));
        assertEquals(75, treasury.balanceOf(RIVALS));
    }

    @Test
    @DisplayName("territory pays once an hour, not once a run")
    void territoryDoesNotPayTwice() throws SQLException {
        turf("docks", FIRM, 150);

        treasury.payTerritoryIncome();
        // A scheduler that runs every thirty seconds must not pay every thirty
        // seconds, and a restart must not be a way to collect again.
        assertEquals(0, treasury.payTerritoryIncome());
        assertEquals(150, treasury.balanceOf(FIRM));
    }

    @Test
    @DisplayName("territory pays again once the hour is up")
    void territoryPaysAgainLater() throws SQLException {
        int id = turf("docks", FIRM, 150);
        treasury.payTerritoryIncome();

        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("UPDATE habnut_rp_turfs "
                + "SET last_paid_at = DATEADD('HOUR', -2, CURRENT_TIMESTAMP) WHERE id = " + id);
        }

        assertEquals(150, treasury.payTerritoryIncome());
        assertEquals(300, treasury.balanceOf(FIRM));
    }

    @Test
    @DisplayName("territory nobody holds pays nobody")
    void unheldTerritoryPaysNothing() throws SQLException {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO habnut_rp_turfs (code, name, income_per_hour) "
                + "VALUES ('empty', 'Empty', 500)");
        }

        assertEquals(0, treasury.payTerritoryIncome());
    }

    @Test
    @DisplayName("income is recorded as income, not as an anonymous credit")
    void incomeIsLabelled() throws SQLException {
        turf("docks", FIRM, 150);
        treasury.payTerritoryIncome();

        assertEquals("turf_income", treasury.history(FIRM, 1).get(0).kind());
    }
}
