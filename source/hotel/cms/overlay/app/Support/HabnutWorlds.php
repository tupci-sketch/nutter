<?php

namespace App\Support;

use Illuminate\Support\Facades\DB;

/**
 * Habnut is two worlds with two entrances: the hotel and the city of
 * Nutropolis. Entering one records it for the game server, which keeps the
 * player inside that world, and sets where they arrive. The player's own
 * hotel home room is remembered while they are in the city.
 */
final class HabnutWorlds
{
    public const HOTEL = 'hotel';

    public const CITY = 'city';

    public static function enter(int $userId, string $world): void
    {
        DB::statement('CREATE TABLE IF NOT EXISTS habnut_user_world (
            user_id INT NOT NULL PRIMARY KEY,
            world VARCHAR(8) NOT NULL DEFAULT \'hotel\',
            hotel_home INT NOT NULL DEFAULT 0,
            updated_at TIMESTAMP NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4');

        $row = DB::table('habnut_user_world')->where('user_id', $userId)->first();
        $current = $row->world ?? self::HOTEL;
        $homeRoom = (int) DB::table('users')->where('id', $userId)->value('home_room');

        if ($world === self::CITY) {
            $spawn = (int) DB::table('habnut_rp_rooms')->where('kind', 'spawn')->min('room_id');
            $hotelHome = $current === self::CITY ? (int) ($row->hotel_home ?? 0) : $homeRoom;
            DB::table('habnut_user_world')->updateOrInsert(
                ['user_id' => $userId],
                ['world' => self::CITY, 'hotel_home' => $hotelHome, 'updated_at' => now()]
            );
            if ($spawn > 0) {
                DB::table('users')->where('id', $userId)->update(['home_room' => $spawn]);
            }

            return;
        }

        if ($current === self::CITY) {
            $home = (int) ($row->hotel_home ?? 0) ?: (int) setting('hotel_home_room');
            DB::table('users')->where('id', $userId)->update(['home_room' => $home]);
        }
        DB::table('habnut_user_world')->updateOrInsert(
            ['user_id' => $userId],
            ['world' => self::HOTEL, 'updated_at' => now()]
        );
    }
}
