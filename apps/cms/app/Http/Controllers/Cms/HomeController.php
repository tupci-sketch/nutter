<?php

namespace App\Http\Controllers\Cms;

use App\Http\Controllers\Controller;
use App\Models\NewsArticle;
use App\Models\User;
use Illuminate\Support\Facades\Cache;

class HomeController extends Controller
{
    /**
     * The landing page.
     *
     * Signed out it introduces the hotel; signed in it becomes the player's own
     * page, showing their figure, balances, who is online and the way in.
     */
    public function index()
    {
        $articles = NewsArticle::published()->latest('published_at')->take(4)->get();

        return view('cms.home', [
            'articles' => $articles,
            'onlineCount' => $this->onlineCount(),
            'staffOnline' => $this->staffOnline(),
            'topPlayers' => $this->topPlayers(),
        ]);
    }

    public function hotel()
    {
        return view('cms.hotel', [
            'onlineCount' => $this->onlineCount(),
        ]);
    }

    /**
     * How many players are in the hotel.
     *
     * Cached briefly: the figure is displayed on every page load but does not
     * need to be exact to the second.
     */
    private function onlineCount(): int
    {
        return Cache::remember('hotel.online_count', 30, function () {
            return User::where('online', true)->count();
        });
    }

    /** Staff currently online, so players can see who to ask for help. */
    private function staffOnline()
    {
        return Cache::remember('hotel.staff_online', 30, function () {
            return User::where('online', true)
                ->where('rank', '>=', User::RANK_MODERATOR)
                ->orderByDesc('rank')
                ->take(8)
                ->get(['id', 'username', 'rank', 'look', 'motto']);
        });
    }

    /** A small leaderboard by achievement score. */
    private function topPlayers()
    {
        return Cache::remember('hotel.top_players', 300, function () {
            return User::orderByDesc('achievement_score')
                ->take(5)
                ->get(['id', 'username', 'look', 'achievement_score']);
        });
    }
}
