package com.habnut.emulator.db;

import com.habnut.emulator.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the hotel's schema and stops.
 *
 * The hotel migrates on start-up, which is right for a running server but
 * useless to anything that needs the schema in place before the hotel comes
 * up: `habnutctl migrate` on an install, and `habnutctl dev up`, which seeds
 * the database between migrating it and starting anything.
 *
 * It reads the same settings the hotel does, so it cannot migrate a different
 * database than the one the hotel will open.
 */
public final class MigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private MigrationRunner() {}

    public static void main(String[] args) {
        try {
            ServerConfig config = ServerConfig.fromEnvironment();
            try (DatabaseManager db = new DatabaseManager(config)) {
                FlywayRunner.migrate(db.getDataSource());
            }
            log.info("Schema is up to date");
        } catch (Exception e) {
            log.error("Could not apply the schema", e);
            // A non-zero exit is what the caller checks: a migration that
            // fails silently leaves a hotel that half works.
            System.exit(1);
        }
    }
}
