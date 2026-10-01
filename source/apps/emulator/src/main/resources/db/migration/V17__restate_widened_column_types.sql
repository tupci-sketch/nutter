-- Restate the types of columns that were renamed and widened in one statement.
--
-- Several earlier migrations used MariaDB's `CHANGE COLUMN old new <type>`,
-- which both renames a column and sets its type. H2 — which the schema tests
-- run against — applies the rename and ignores the type. The result was a
-- schema that differed depending on where it was built: `wall_position` was
-- VARCHAR(64) on a real hotel and CHAR(1) under test, so a wall item that the
-- hotel saves fine was rejected by the tests meant to prove it saves.
--
-- Restating each type on its own leaves MariaDB exactly as it was and brings
-- H2 into line, so both ends up with the shape the services were written for.
-- MODIFY COLUMN is a no-op when the column already has that type.

ALTER TABLE habnut_wall_items
    MODIFY COLUMN wall_position VARCHAR(64) NOT NULL DEFAULT '';

ALTER TABLE habnut_room_models
    MODIFY COLUMN heightmap TEXT NOT NULL;

ALTER TABLE habnut_users
    MODIFY COLUMN figure VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE habnut_users
    MODIFY COLUMN machine_id VARCHAR(128) NULL;

ALTER TABLE habnut_pets
    MODIFY COLUMN pet_type VARCHAR(32) NOT NULL;

ALTER TABLE habnut_user_achievements
    MODIFY COLUMN achievement_code VARCHAR(64) NOT NULL;

ALTER TABLE habnut_user_badges
    MODIFY COLUMN badge_code VARCHAR(64) NOT NULL;

ALTER TABLE habnut_wired_variables
    MODIFY COLUMN var_name VARCHAR(64) NOT NULL;

ALTER TABLE habnut_wired_variables
    MODIFY COLUMN var_type VARCHAR(16) NOT NULL;

ALTER TABLE habnut_wired_variables
    MODIFY COLUMN text_value VARCHAR(512) NULL;

ALTER TABLE habnut_transactions
    MODIFY COLUMN reason VARCHAR(255) NOT NULL DEFAULT '';
