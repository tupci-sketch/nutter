package com.habnut.emulator.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

public final class FlywayRunner {

    private static final Logger log = LoggerFactory.getLogger(FlywayRunner.class);

    private FlywayRunner() {}

    public static void migrate(DataSource dataSource) {
        log.info("Running Flyway migrations");

        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)
            .validateOnMigrate(true)
            .outOfOrder(false)
            .cleanDisabled(true)
            .encoding("UTF-8")
            .placeholderReplacement(false)
            .load();

        MigrationInfo[] pending = flyway.info().pending();
        if (pending.length > 0) {
            log.info("{} pending migrations", pending.length);
            for (MigrationInfo info : pending) {
                log.info("  Pending: {} — {}", info.getVersion(), info.getDescription());
            }
        }

        MigrateResult result = flyway.migrate();
        log.info("Flyway migration complete: {} migrations applied, current version {}",
            result.migrationsExecuted, result.targetSchemaVersion);

        for (MigrationInfo info : flyway.info().applied()) {
            log.debug("Applied migration: {} ({}) in {}ms",
                info.getVersion(), info.getDescription(), info.getExecutionTime());
        }
    }
}
