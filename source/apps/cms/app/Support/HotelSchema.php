<?php

namespace App\Support;

use Illuminate\Support\Facades\Schema;

/**
 * Whether the hotel's own schema is in this database.
 *
 * Every table named habnut_* belongs to the hotel: the emulator's migrations
 * create it, own its shape, and the players' data lives in it. The website's
 * migrations describe many of the same tables, but only so the website's own
 * tests — which run against an empty SQLite database with no hotel in it —
 * have the tables the website reads.
 *
 * Against a real hotel those descriptions must do nothing. Before this, they
 * re-added columns the hotel already had (so the website died on its first
 * start), retyped one of the hotel's columns, dropped and recreated the
 * leaderboards, and every rollback dropped the hotel's tables outright —
 * habnut_users included.
 *
 * The emulator's migration tool records its history in flyway_schema_history,
 * so that table being present is the sign the hotel is here.
 */
final class HotelSchema
{
    public static function present(): bool
    {
        return Schema::hasTable('flyway_schema_history');
    }

    /**
     * Drop a hotel table only where this website created it: in the tests'
     * database, never in a hotel's.
     */
    public static function dropIfOurs(string $table): void
    {
        if (! self::present()) {
            Schema::dropIfExists($table);
        }
    }
}
