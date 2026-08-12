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

    private final HikariDataSource dataSource;

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

    public int getActiveConnections() {
        return dataSource.getHikariPoolMXBean().getActiveConnections();
    }

    public int getIdleConnections() {
        return dataSource.getHikariPoolMXBean().getIdleConnections();
    }

    public int getTotalConnections() {
        return dataSource.getHikariPoolMXBean().getTotalConnections();
    }

    @Override
    public void close() {
        log.info("Closing database connection pool");
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
