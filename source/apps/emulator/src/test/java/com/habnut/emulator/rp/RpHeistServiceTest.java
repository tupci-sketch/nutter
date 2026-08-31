package com.habnut.emulator.rp;

import com.habnut.emulator.db.DatabaseManager;
import com.habnut.emulator.db.MigrationHarness;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Heists, and the money they pay into.
 *
 * A heist is meant to be a contest rather than a timer with a payout on the end,
 * so most of what is worth pinning down is about the ways it can be lost: not
 * enough crew, not enough police in the city to bother robbing it, an alarm that
 * gives the police a real window, and a cooldown that stops one target being
 * farmed.
 */
@DisplayName("Heists")
class RpHeistServiceTest {

    private static final int CROOKS = 1;
    private static final int POLICE = 2;

    private DatabaseManager db;
    private RpHeistService heists;
    private RpTreasuryService treasury;

    private long boss;
    private long crook;
    private long officer;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:heists_" + System.nanoTime()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE,KEY,YEAR,MONTH,DAY,HOUR,MINUTE,SECOND,ACTION");
        ds.setUser("sa");
        db = new DatabaseManager(ds);

        try (Connection conn = db.getConnection()) {
            MigrationHarness.applyAll(conn);
            seed(conn);
        }

        treasury = new RpTreasuryService(db);
        heists = new RpHeistService(db, treasury, new RpCharacterService(db));
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP ALL OBJECTS");
        }
    }

    private void seed(Connection conn) throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("INSERT INTO habnut_users (id, username, email, password_hash, figure) "
                + "VALUES (1, 'boss', 'a@b.c', 'x', 'hd-180-1'), "
                + "(2, 'crook', 'b@b.c', 'x', 'hd-180-1'), (3, 'officer', 'c@b.c', 'x', 'hd-180-1'), "
                + "(4, 'spare', 'd@b.c', 'x', 'hd-180-1')");
            s.execute("INSERT INTO habnut_rp_factions (id, name, tag) VALUES "
                + "(" + CROOKS + ", 'The Firm', 'criminal'), "
                + "(" + POLICE + ", 'City Police', 'police')");
        }

        boss = character(conn, 1, "Boss", CROOKS);
        crook = character(conn, 2, "Crook", CROOKS);
        officer = character(conn, 3, "Officer", POLICE);

        try (Statement s = conn.createStatement()) {
            s.execute("UPDATE habnut_rp_factions SET leader_id = " + boss + " WHERE id = " + CROOKS);
        }
    }

    private long character(Connection conn, long userId, String name, int factionId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO habnut_rp_characters (user_id, name, surname, faction_id) VALUES (?,?,?,?)",
            Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, name);
            ps.setString(3, "Nutkin");
            ps.setInt(4, factionId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                try (PreparedStatement m = conn.prepareStatement(
                    "INSERT INTO habnut_rp_faction_members (faction_id, character_id) VALUES (?,?)")) {
                    m.setInt(1, factionId);
                    m.setLong(2, id);
                    m.executeUpdate();
                }
                return id;
            }
        }
    }

    /** The target with the smallest crew requirement, so most tests can use it. */
    private RpHeistService.Target smallJob() {
        return heists.targets(9).stream()
            .filter(t -> "corner_shop".equals(t.code())).findFirst().orElseThrow();
    }

    private RpHeistService.Target bigJob() {
        return heists.targets(9).stream()
            .filter(t -> "city_bank".equals(t.code())).findFirst().orElseThrow();
    }

    // ─── what can be robbed ─────────────────────────────────────────────────

    @Test
    @DisplayName("a hotel opens with targets to rob")
    void targetsAreSeeded() {
        assertTrue(heists.targets(9).size() >= 4);
    }

    @Test
    @DisplayName("nothing is worth robbing when nobody is policing")
    void targetsCloseWithoutPolice() {
        // A city with no police should be one where the banks are shut, not
        // free money.
        List<RpHeistService.Target> targets = heists.targets(0);

        assertTrue(targets.stream().noneMatch(RpHeistService.Target::available));
        assertEquals("not_enough_police_on_duty",
            targets.get(0).unavailableReason());
    }

    @Test
    @DisplayName("the smallest job opens first as police come on duty")
    void biggerJobsNeedMorePolice() {
        List<RpHeistService.Target> withOne = heists.targets(1);

        assertTrue(withOne.stream()
            .filter(t -> "corner_shop".equals(t.code())).findFirst().orElseThrow().available());
        assertFalse(withOne.stream()
            .filter(t -> "city_bank".equals(t.code())).findFirst().orElseThrow().available());
    }

    // ─── planning ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("planning a job puts its leader on the crew")
    void planningAddsTheLeader() {
        RpHeistService.Result result = heists.plan(smallJob().id(), CROOKS, boss, 9);

        assertTrue(result.ok(), result.reason());
        assertEquals(1, heists.active().get(0).crewSize());
    }

    @Test
    @DisplayName("a faction runs one job at a time")
    void oneJobPerFaction() {
        heists.plan(smallJob().id(), CROOKS, boss, 9);

        // A faction spread across three banks is not a heist, it is a way of
        // avoiding the police response.
        RpHeistService.Result second = heists.plan(bigJob().id(), CROOKS, boss, 9);
        assertFalse(second.ok());
        assertEquals("faction_already_has_a_job_open", second.reason());
    }

    @Test
    @DisplayName("a target being robbed cannot also be robbed by somebody else")
    void aTargetTakesOneCrew() {
        int targetId = smallJob().id();
        heists.plan(targetId, CROOKS, boss, 9);

        assertEquals("already_being_robbed",
            heists.targets(9).stream().filter(t -> t.id() == targetId)
                .findFirst().orElseThrow().unavailableReason());
    }

    @Test
    @DisplayName("a crew cannot grow past the target's limit")
    void crewHasALimit() throws SQLException {
        long heistId = heists.plan(smallJob().id(), CROOKS, boss, 9).heistId();

        // The corner shop takes three; the leader is already one of them.
        heists.join(heistId, crook);
        heists.join(heistId, officer);
        long extra;
        try (Connection conn = db.getConnection()) {
            extra = character(conn, 4, "Spare", CROOKS);
        }

        RpHeistService.Result result = heists.join(heistId, extra);
        assertFalse(result.ok());
        assertEquals("crew_is_full", result.reason());
    }

    @Test
    @DisplayName("the last person to leave calls the job off")
    void anEmptyCrewEndsTheJob() {
        long heistId = heists.plan(smallJob().id(), CROOKS, boss, 9).heistId();

        heists.leave(heistId, boss);

        assertTrue(heists.active().isEmpty());
    }

    // ─── the job ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a job will not start without enough crew")
    void startNeedsACrew() {
        long heistId = heists.plan(bigJob().id(), CROOKS, boss, 9).heistId();

        RpHeistService.Result result = heists.start(heistId, boss);
        assertFalse(result.ok());
        assertEquals("not_enough_crew", result.reason());
    }

    @Test
    @DisplayName("only whoever planned the job starts it")
    void onlyTheLeaderStarts() {
        long heistId = heists.plan(smallJob().id(), CROOKS, boss, 9).heistId();
        heists.join(heistId, crook);

        RpHeistService.Result result = heists.start(heistId, crook);
        assertFalse(result.ok());
        assertEquals("only_the_leader_starts_the_job", result.reason());
    }

    @Test
    @DisplayName("starting a job sets the alarm before the finish")
    void theAlarmComesFirst() throws SQLException {
        long heistId = heists.plan(smallJob().id(), CROOKS, boss, 9).heistId();
        heists.start(heistId, boss);

        // The gap between the two is the window police have to arrive, and is
        // what makes a heist a contest rather than a countdown.
        try (Connection c = db.getConnection();
             ResultSet rs = c.createStatement().executeQuery(
                 "SELECT alarm_at, resolves_at FROM habnut_rp_heists WHERE id = " + heistId)) {
            assertTrue(rs.next());
            assertTrue(rs.getTimestamp("alarm_at").before(rs.getTimestamp("resolves_at")));
        }
    }

    @Test
    @DisplayName("a crew cannot be added once the job has started")
    void noLatecomers() {
        long heistId = heists.plan(smallJob().id(), CROOKS, boss, 9).heistId();
        heists.start(heistId, boss);

        RpHeistService.Result result = heists.join(heistId, crook);
        assertFalse(result.ok());
        assertEquals("job_already_started", result.reason());
    }

    // ─── police ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("police cannot stop a job before its alarm has gone")
    void noStoppingItBeforeAnybodyKnows() {
        long heistId = heists.plan(smallJob().id(), CROOKS, boss, 9).heistId();
        heists.start(heistId, boss);

        // An officer standing in the shop before anybody knows a robbery is
        // happening has not responded to it.
        RpHeistService.Result result = heists.foil(heistId, officer);
        assertFalse(result.ok());
        assertEquals("alarm_has_not_gone_yet", result.reason());
    }

    @Test
    @DisplayName("police who arrive after the alarm stop the job")
    void policeStopIt() throws SQLException {
        long heistId = heists.plan(smallJob().id(), CROOKS, boss, 9).heistId();
        heists.start(heistId, boss);
        alarmHasGone(heistId);

        assertTrue(heists.foil(heistId, officer).ok());

        assertEquals("foiled", stateOf(heistId));
        assertEquals(0, treasury.balanceOf(CROOKS), "a stopped job pays nothing");
    }

    @Test
    @DisplayName("a job stopped by police does not later pay out")
    void aStoppedJobStaysStopped() throws SQLException {
        long heistId = heists.plan(smallJob().id(), CROOKS, boss, 9).heistId();
        heists.start(heistId, boss);
        alarmHasGone(heistId);
        heists.foil(heistId, officer);

        timeHasPassed(heistId);
        assertTrue(heists.resolveFinishedHeists().isEmpty());
        assertEquals(0, treasury.balanceOf(CROOKS));
    }

    // ─── getting away with it ───────────────────────────────────────────────

    @Test
    @DisplayName("a job nobody stops pays the faction and the crew")
    void aSuccessfulJobPays() throws SQLException {
        long heistId = heists.plan(smallJob().id(), CROOKS, boss, 9).heistId();
        heists.join(heistId, crook);
        heists.start(heistId, boss);
        timeHasPassed(heistId);

        List<RpHeistService.Payout> payouts = heists.resolveFinishedHeists();

        assertEquals(1, payouts.size());
        RpHeistService.Payout payout = payouts.get(0);
        assertTrue(payout.total() > 0);
        assertEquals(payout.factionShare(), treasury.balanceOf(CROOKS));
        assertEquals(2, payout.crewShares().size(), "both members should have been paid");

        // The crew's shares plus the faction's cannot exceed the take.
        long paidToCrew = payout.crewShares().stream().mapToLong(s -> s[1]).sum();
        assertTrue(paidToCrew + payout.factionShare() <= payout.total());
    }

    @Test
    @DisplayName("a job is only paid once, however often resolution runs")
    void aJobPaysOnce() throws SQLException {
        long heistId = heists.plan(smallJob().id(), CROOKS, boss, 9).heistId();
        heists.start(heistId, boss);
        timeHasPassed(heistId);

        heists.resolveFinishedHeists();
        long afterFirst = treasury.balanceOf(CROOKS);

        // Two schedulers overlapping must not pay the same heist twice.
        assertTrue(heists.resolveFinishedHeists().isEmpty());
        assertEquals(afterFirst, treasury.balanceOf(CROOKS));
    }

    @Test
    @DisplayName("a robbed target cannot be robbed again straight away")
    void targetsCoolDown() throws SQLException {
        int targetId = smallJob().id();
        long heistId = heists.plan(targetId, CROOKS, boss, 9).heistId();
        heists.start(heistId, boss);
        timeHasPassed(heistId);
        heists.resolveFinishedHeists();

        assertEquals("too_soon_since_the_last_one",
            heists.targets(9).stream().filter(t -> t.id() == targetId)
                .findFirst().orElseThrow().unavailableReason());
    }

    @Test
    @DisplayName("the crew are paid their share in cash")
    void crewAreActuallyPaid() throws SQLException {
        long heistId = heists.plan(smallJob().id(), CROOKS, boss, 9).heistId();
        heists.join(heistId, crook);
        heists.start(heistId, boss);
        timeHasPassed(heistId);
        heists.resolveFinishedHeists();

        try (Connection c = db.getConnection();
             ResultSet rs = c.createStatement().executeQuery(
                 "SELECT SUM(cash_balance) FROM habnut_rp_characters WHERE id IN ("
                 + boss + ", " + crook + ")")) {
            assertTrue(rs.next());
            assertTrue(rs.getLong(1) > 0, "the crew should have been paid, not just the faction");
        }
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    /** Winds the alarm back so it has already gone. */
    private void alarmHasGone(long heistId) throws SQLException {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("UPDATE habnut_rp_heists "
                + "SET alarm_at = DATEADD('SECOND', -5, CURRENT_TIMESTAMP) WHERE id = " + heistId);
        }
    }

    /** Winds the clock past the job's finish. */
    private void timeHasPassed(long heistId) throws SQLException {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("UPDATE habnut_rp_heists SET "
                + "alarm_at = DATEADD('SECOND', -30, CURRENT_TIMESTAMP), "
                + "resolves_at = DATEADD('SECOND', -1, CURRENT_TIMESTAMP) WHERE id = " + heistId);
        }
    }

    private String stateOf(long heistId) throws SQLException {
        try (Connection c = db.getConnection();
             ResultSet rs = c.createStatement().executeQuery(
                 "SELECT state FROM habnut_rp_heists WHERE id = " + heistId)) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }
}
