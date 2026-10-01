<?php

namespace Tests\Unit;

use Tests\TestCase;

/**
 * The hotel owns its schema; the website mirrors it.
 *
 * Both halves ship migrations, and forty-two tables were defined by both. On a
 * real install they run against one database, so whichever went second either
 * failed outright or left the two halves disagreeing about the shape of a
 * table — which is how the website came to keep its own `users` and its own
 * `bans`, and how a player could be banned on the site and walk straight back
 * into the hotel.
 *
 * The rule is simple enough to check: any table the hotel also defines must be
 * created here only when it is not already there.
 */
class SchemaOwnershipTest extends TestCase
{
    private const HOTEL_MIGRATIONS = __DIR__.'/../../../emulator/src/main/resources/db/migration';

    private const CMS_MIGRATIONS = __DIR__.'/../../database/migrations';

    /** Tables the hotel's own migrations create. */
    private function hotelTables(): array
    {
        if (! is_dir(self::HOTEL_MIGRATIONS)) {
            $this->markTestSkipped('The hotel migrations are not beside this checkout.');
        }

        $tables = [];
        foreach (glob(self::HOTEL_MIGRATIONS.'/*.sql') as $file) {
            preg_match_all(
                '/CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(habnut_\w+)/i',
                file_get_contents($file),
                $matches
            );
            $tables = array_merge($tables, array_map('strtolower', $matches[1]));
        }

        return array_unique($tables);
    }

    /** @test */
    public function the_website_never_redefines_a_table_the_hotel_owns(): void
    {
        $hotel = $this->hotelTables();
        $this->assertNotEmpty($hotel, 'No hotel tables found; the check would pass vacuously.');

        $unguarded = [];

        foreach (glob(self::CMS_MIGRATIONS.'/*.php') as $file) {
            $source = file_get_contents($file);

            preg_match_all("/Schema::create\('(habnut_\w+)'/", $source, $matches, PREG_OFFSET_CAPTURE);

            foreach ($matches[1] as $i => [$table, $_]) {
                if (! in_array(strtolower($table), $hotel, true)) {
                    continue;
                }

                // The guard sits immediately before the create, so look at the
                // line above rather than anywhere in the file.
                $createdAt = $matches[0][$i][1];
                $preceding = substr($source, max(0, $createdAt - 200), 200);

                if (! str_contains($preceding, "Schema::hasTable('{$table}')")) {
                    $unguarded[] = basename($file).': '.$table;
                }
            }
        }

        $this->assertSame([], $unguarded,
            'These tables belong to the hotel and must be created only when absent '
            ."(wrap them in `if (! Schema::hasTable('…'))`):\n  ".implode("\n  ", $unguarded));
    }

    /** @test */
    public function the_website_keeps_no_second_table_for_something_the_hotel_already_records(): void
    {
        $duplicates = ['users', 'bans', 'rooms', 'groups', 'transactions'];
        $found = [];

        foreach (glob(self::CMS_MIGRATIONS.'/*.php') as $file) {
            $source = file_get_contents($file);
            foreach ($duplicates as $table) {
                if (preg_match("/Schema::create\('{$table}'/", $source)) {
                    $found[] = basename($file).": {$table}";
                }
            }
        }

        $this->assertSame([], $found,
            "The website must use the hotel's table, not keep its own alongside it:\n  "
            .implode("\n  ", $found));
    }
}
