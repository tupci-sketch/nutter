<?php

namespace App\Http\Controllers\Cms;

use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Support\Facades\DB;

/**
 * The pages that show the hotel to itself: who runs it, and how it is doing.
 *
 * Both are public. A hotel that will not say who its staff are, or how many
 * people are actually in it, invites players to assume the worst on both counts.
 */
class CommunityController extends Controller
{
    /** How long the figures are held before being worked out again. */
    private const CACHE_SECONDS = 60;

    /** The staff team, most senior first. */
    public function staff()
    {
        $staff = User::where('rank', '>=', User::RANK_HELPER)
            ->orderByDesc('rank')
            ->orderBy('username')
            ->get(['id', 'username', 'motto', 'figure', 'rank', 'online', 'member_since']);

        return view('cms.community.staff', [
            'groups' => $staff->groupBy(fn (User $u) => $u->rankName()),
            'onlineCount' => $staff->where('online', true)->count(),
        ]);
    }

    /**
     * How the hotel is doing.
     *
     * Counted behind a short cache: these are whole-table counts, and a page
     * anybody can load should not be a way to make the database work hard.
     */
    public function stats()
    {
        $stats = cache()->remember('hotel-stats', self::CACHE_SECONDS, fn () => [
            'players' => User::count(),
            'online' => User::where('online', true)->count(),
            'rooms' => DB::table('habnut_rooms')->count(),
            'groups' => DB::table('habnut_groups')->count(),
            'joinedToday' => User::whereDate('member_since', today())->count(),
        ]);

        return view('cms.community.stats', [
            'stats' => $stats,
            'topPlayers' => $this->topPlayers(),
            'busiestRooms' => $this->busiestRooms(),
            'leaderboards' => $this->leaderboards(),
        ]);
    }

    /** The players with the most achievement score. */
    private function topPlayers()
    {
        return cache()->remember('hotel-top-players', self::CACHE_SECONDS, fn () => User::query()
            ->where('achievement_score', '>', 0)
            ->orderByDesc('achievement_score')
            ->limit(10)
            ->get(['id', 'username', 'figure', 'motto', 'achievement_score']));
    }

    /** Rooms with the most visitors right now. */
    private function busiestRooms()
    {
        return cache()->remember('hotel-busiest-rooms', self::CACHE_SECONDS, fn () => DB::table('habnut_rooms')
            ->leftJoin('habnut_users as users', 'users.id', '=', 'habnut_rooms.owner_id')
            ->orderByDesc('habnut_rooms.user_count')
            ->limit(10)
            ->get([
                'habnut_rooms.id', 'habnut_rooms.name', 'habnut_rooms.description',
                'habnut_rooms.user_count', 'habnut_rooms.max_users',
                'users.username as owner',
            ]));
    }

    /** The best players at each game, so the games have something to play for. */
    private function leaderboards()
    {
        return cache()->remember('hotel-leaderboards', self::CACHE_SECONDS, function () {
            return DB::table('habnut_leaderboards as l')
                ->join('habnut_users as u', 'u.id', '=', 'l.user_id')
                ->where('l.period', 'all_time')
                ->orderBy('l.game_type')
                ->orderByDesc('l.wins')
                ->orderByDesc('l.total_score')
                ->get(['l.game_type', 'l.matches_played', 'l.wins', 'l.total_score', 'u.username'])
                ->groupBy('game_type')
                ->map(fn ($rows) => $rows->take(5));
        });
    }
}
