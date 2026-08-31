package com.habnut.emulator.economy;

import com.habnut.emulator.db.DatabaseManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Economy integrity against a real database.
 *
 * These are the properties money depends on: a balance never goes negative, an
 * idempotency key is honoured exactly once, transaction rows are append-only,
 * and concurrent writers cannot lose an update. H2 in MySQL compatibility mode
 * provides real row locking and INSERT IGNORE semantics, so the service runs
 * unmodified — no mocks stand in for the database.
 */
@DisplayName("Economy integrity")
class EconomyIntegrityTest {

    private static final long USER = 1L;

    private DatabaseManager db;
    private TransactionService svc;
    private String dbName;

    @BeforeEach
    void setUp() throws SQLException {
        // A distinct database per test keeps concurrent cases isolated.
        dbName = "economy_" + System.nanoTime();
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        db = new DatabaseManager(ds);
        svc = new TransactionService(db);
        createSchema();
        seedUser(USER, 1_000, 50, 0, 0);
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP ALL OBJECTS");
        }
    }

    private void createSchema() throws SQLException {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE habnut_users (
                    id BIGINT PRIMARY KEY,
                    credits BIGINT NOT NULL DEFAULT 0,
                    diamonds BIGINT NOT NULL DEFAULT 0,
                    nut_points BIGINT NOT NULL DEFAULT 0,
                    seasonal_currency BIGINT NOT NULL DEFAULT 0
                )""");
            s.execute("""
                CREATE TABLE habnut_transactions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    currency VARCHAR(20) NOT NULL,
                    amount BIGINT NOT NULL,
                    reason VARCHAR(255),
                    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
                    transaction_hash VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
        }
    }

    private void seedUser(long id, long credits, long diamonds, long nut, long seasonal)
            throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO habnut_users (id, credits, diamonds, nut_points, seasonal_currency) " +
                 "VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, id); ps.setLong(2, credits); ps.setLong(3, diamonds);
            ps.setLong(4, nut); ps.setLong(5, seasonal);
            ps.executeUpdate();
        }
    }

    private int transactionCount() throws SQLException {
        try (Connection c = db.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM habnut_transactions")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // ─── balances ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("a grant increases the balance and records one row")
    void grantIncreasesBalance() throws SQLException {
        svc.grant(USER, TransactionService.Currency.CREDITS, 500, "test grant", "grant-1");

        assertEquals(1_500, svc.getBalance(USER).credits());
        assertEquals(1, transactionCount());
    }

    @Test
    @DisplayName("a debit decreases the balance and records a negative row")
    void debitDecreasesBalance() throws SQLException {
        svc.debit(USER, TransactionService.Currency.CREDITS, 300, "test debit", "debit-1");

        assertEquals(700, svc.getBalance(USER).credits());

        try (Connection c = db.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT amount FROM habnut_transactions")) {
            rs.next();
            assertEquals(-300, rs.getLong("amount"), "a debit is stored as a negative amount");
        }
    }

    @Test
    @DisplayName("each currency moves independently")
    void currenciesAreIndependent() throws SQLException {
        svc.grant(USER, TransactionService.Currency.DIAMONDS, 10, "d", "d-1");
        svc.grant(USER, TransactionService.Currency.NUT_POINTS, 7, "n", "n-1");

        TransactionService.Balance b = svc.getBalance(USER);
        assertEquals(1_000, b.credits(), "credits must be untouched");
        assertEquals(60, b.diamonds());
        assertEquals(7, b.nutPoints());
        assertEquals(0, b.seasonal());
    }

    @Test
    @DisplayName("an unknown user reports a zero balance rather than failing")
    void unknownUserBalance() {
        TransactionService.Balance b = svc.getBalance(9_999L);
        assertEquals(0, b.credits());
        assertEquals(0, b.diamonds());
    }

    // ─── overdraft protection ───────────────────────────────────────────────

    @Test
    @DisplayName("a debit beyond the balance is rejected")
    void overdraftIsRejected() {
        assertThrows(IllegalStateException.class,
            () -> svc.debit(USER, TransactionService.Currency.CREDITS, 1_001, "too much", "over-1"));
    }

    @Test
    @DisplayName("a rejected debit leaves neither balance nor ledger changed")
    void rejectedDebitRollsBackCompletely() throws SQLException {
        assertThrows(IllegalStateException.class,
            () -> svc.debit(USER, TransactionService.Currency.CREDITS, 5_000, "too much", "over-2"));

        assertEquals(1_000, svc.getBalance(USER).credits(), "balance must be unchanged");
        assertEquals(0, transactionCount(), "no ledger row may survive a rejected debit");
    }

    @Test
    @DisplayName("a debit of the exact balance is allowed and lands on zero")
    void exactBalanceDebitIsAllowed() throws SQLException {
        svc.debit(USER, TransactionService.Currency.CREDITS, 1_000, "spend all", "exact-1");

        assertEquals(0, svc.getBalance(USER).credits());
    }

    @Test
    @DisplayName("zero and negative amounts are refused by both operations")
    void nonPositiveAmountsRefused() {
        assertThrows(IllegalArgumentException.class,
            () -> svc.grant(USER, TransactionService.Currency.CREDITS, 0, "zero", "z-1"));
        assertThrows(IllegalArgumentException.class,
            () -> svc.grant(USER, TransactionService.Currency.CREDITS, -5, "negative", "z-2"));
        assertThrows(IllegalArgumentException.class,
            () -> svc.debit(USER, TransactionService.Currency.CREDITS, 0, "zero", "z-3"));
        assertThrows(IllegalArgumentException.class,
            () -> svc.debit(USER, TransactionService.Currency.CREDITS, -5, "negative", "z-4"));
    }

    // ─── idempotency ────────────────────────────────────────────────────────

    @Test
    @DisplayName("replaying a grant with the same key applies it once")
    void grantIsIdempotent() throws SQLException {
        svc.grant(USER, TransactionService.Currency.CREDITS, 500, "purchase", "order-42");
        svc.grant(USER, TransactionService.Currency.CREDITS, 500, "purchase", "order-42");
        svc.grant(USER, TransactionService.Currency.CREDITS, 500, "purchase", "order-42");

        assertEquals(1_500, svc.getBalance(USER).credits(),
            "a retried payment callback must not pay out three times");
        assertEquals(1, transactionCount());
    }

    @Test
    @DisplayName("replaying a debit with the same key applies it once")
    void debitIsIdempotent() throws SQLException {
        svc.debit(USER, TransactionService.Currency.CREDITS, 200, "buy", "cart-7");
        svc.debit(USER, TransactionService.Currency.CREDITS, 200, "buy", "cart-7");

        assertEquals(800, svc.getBalance(USER).credits());
        assertEquals(1, transactionCount());
    }

    @Test
    @DisplayName("distinct keys apply separately")
    void distinctKeysBothApply() throws SQLException {
        svc.grant(USER, TransactionService.Currency.CREDITS, 100, "a", "key-a");
        svc.grant(USER, TransactionService.Currency.CREDITS, 100, "b", "key-b");

        assertEquals(1_200, svc.getBalance(USER).credits());
        assertEquals(2, transactionCount());
    }

    @Test
    @DisplayName("a null key is generated per call, so repeats are not collapsed")
    void nullKeyGeneratesAFreshKey() throws SQLException {
        svc.grant(USER, TransactionService.Currency.CREDITS, 100, "reward", null);
        svc.grant(USER, TransactionService.Currency.CREDITS, 100, "reward", null);

        assertEquals(1_200, svc.getBalance(USER).credits(),
            "two unrelated rewards must both land");
        assertEquals(2, transactionCount());
    }

    // ─── ledger immutability ────────────────────────────────────────────────

    @Test
    @DisplayName("the ledger is append-only across a sequence of movements")
    void ledgerIsAppendOnly() throws SQLException {
        svc.grant(USER, TransactionService.Currency.CREDITS, 100, "a", "s-1");
        svc.debit(USER, TransactionService.Currency.CREDITS, 50, "b", "s-2");
        svc.grant(USER, TransactionService.Currency.CREDITS, 25, "c", "s-3");

        List<Long> amounts = new ArrayList<>();
        try (Connection c = db.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT amount FROM habnut_transactions ORDER BY id")) {
            while (rs.next()) amounts.add(rs.getLong("amount"));
        }

        assertEquals(List.of(100L, -50L, 25L), amounts,
            "every movement keeps its own row; none is rewritten or removed");
        assertEquals(1_075, svc.getBalance(USER).credits(),
            "the balance equals the sum of the ledger");
    }

    @Test
    @DisplayName("every transaction carries a hash")
    void everyRowIsHashed() throws SQLException {
        svc.grant(USER, TransactionService.Currency.CREDITS, 100, "a", "h-1");

        try (Connection c = db.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT transaction_hash FROM habnut_transactions")) {
            rs.next();
            assertEquals(64, rs.getString("transaction_hash").length(), "SHA-256 hex");
        }
    }

    // ─── concurrency ────────────────────────────────────────────────────────

    @Test
    @DisplayName("concurrent grants do not lose updates")
    void concurrentGrantsAllLand() throws Exception {
        int threads = 8;
        int perThread = 10;

        runConcurrently(threads, t -> {
            for (int i = 0; i < perThread; i++) {
                svc.grant(USER, TransactionService.Currency.CREDITS, 10,
                    "concurrent", "grant-" + t + "-" + i);
            }
        });

        assertEquals(1_000 + threads * perThread * 10, svc.getBalance(USER).credits(),
            "a lost update would leave the balance short");
        assertEquals(threads * perThread, transactionCount());
    }

    @Test
    @DisplayName("concurrent replays of one key still apply exactly once")
    void concurrentIdempotencyHoldsUnderRace() throws Exception {
        runConcurrently(8, t -> {
            for (int i = 0; i < 5; i++) {
                svc.grant(USER, TransactionService.Currency.CREDITS, 250, "payout", "single-key");
            }
        });

        assertEquals(1_250, svc.getBalance(USER).credits(),
            "forty concurrent replays of one key must pay out once");
        assertEquals(1, transactionCount());
    }

    @Test
    @DisplayName("concurrent debits cannot drive the balance negative")
    void concurrentDebitsCannotOverdraw() throws Exception {
        AtomicInteger rejected = new AtomicInteger();

        // Twenty threads each try to take 100 from a balance of 1000.
        runConcurrently(20, t -> {
            try {
                svc.debit(USER, TransactionService.Currency.CREDITS, 100, "race", "spend-" + t);
            } catch (IllegalStateException e) {
                rejected.incrementAndGet();
            }
        });

        long remaining = svc.getBalance(USER).credits();
        assertTrue(remaining >= 0, "balance went negative: " + remaining);
        assertEquals(10, rejected.get(), "exactly half the attempts should be refused");
        assertEquals(0, remaining);
    }

    /** Runs body on the given number of threads, failing the test on any error. */
    private void runConcurrently(int threads, ThrowingIntConsumer body) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int id = t;
            futures.add(pool.submit(() -> {
                start.await();
                body.accept(id);
                return null;
            }));
        }

        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "workers did not finish in time");

        for (Future<?> f : futures) {
            f.get(); // surfaces any exception the worker did not handle
        }
    }

    @FunctionalInterface
    private interface ThrowingIntConsumer {
        void accept(int value) throws Exception;
    }
}
