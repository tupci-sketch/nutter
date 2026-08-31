package com.habnut.emulator.db;

import com.habnut.emulator.config.ServerConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private final DataSource dataSource;

    /**
     * Wraps an already-configured data source.
     *
     * The server builds its pool from {@link ServerConfig} using the other
     * constructor; this one lets tests supply an in-memory database so
     * transaction semantics can be exercised without a MariaDB instance.
     */
    public DatabaseManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public DatabaseManager(ServerConfig config) {
        log.info("Initialising database connection pool");
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.dbUrl);
        hikari.setUsername(config.dbUsername);
        hikari.setPassword(config.dbPassword);
        hikari.setDriverClassName("org.mariadb.jdbc.Driver");

        hikari.setMinimumIdle(config.dbPoolMinIdle);
        hikari.setMaximumPoolSize(config.dbPoolMaxSize);
        hikari.setConnectionTimeout(config.dbConnectionTimeoutMs);
        hikari.setIdleTimeout(config.dbIdleTimeoutMs);
        hikari.setMaxLifetime(config.dbMaxLifetimeMs);
        hikari.setKeepaliveTime(config.dbKeepaliveTimeMs);

        hikari.setPoolName("habnut-db-pool");

        // Validate connection on borrow
        hikari.setConnectionTestQuery("SELECT 1");
        hikari.setInitializationFailTimeout(10000);

        // MariaDB-specific tuning
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikari.addDataSourceProperty("useServerPrepStmts", "true");
        hikari.addDataSourceProperty("characterEncoding", "utf8mb4");

        this.dataSource = new HikariDataSource(hikari);
        log.info("Database connection pool ready: maxPoolSize={}", config.dbPoolMaxSize);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public boolean isHealthy() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (SQLException e) {
            log.warn("Database health check failed", e);
            return false;
        }
    }

    // Pool statistics are reported to Prometheus.  A data source supplied
    // directly (as in tests) exposes no pool bean, so these report zero rather
    // than failing the metrics scrape.

    public int getActiveConnections() {
        return dataSource instanceof HikariDataSource h ? h.getHikariPoolMXBean().getActiveConnections() : 0;
    }

    public int getIdleConnections() {
        return dataSource instanceof HikariDataSource h ? h.getHikariPoolMXBean().getIdleConnections() : 0;
    }

    public int getTotalConnections() {
        return dataSource instanceof HikariDataSource h ? h.getHikariPoolMXBean().getTotalConnections() : 0;
    }

    @Override
    public void close() {
        if (dataSource instanceof HikariDataSource h && !h.isClosed()) {
            log.info("Closing database connection pool");
            h.close();
        }
    }
}
