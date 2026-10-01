<?php

namespace App\Http\Controllers\Cms;

use App\Http\Controllers\Controller;
use App\Models\NewsArticle;
use App\Models\User;
use App\Services\SessionTicketService;
use Illuminate\Http\Request;
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

    /**
     * The way in.
     *
     * A signed-in player gets a ticket made for them here and is sent straight
     * through to the game with it, so they sign in once, on the website, and
     * the hotel knows who walked in. Nobody types a ticket and nobody has a
     * second account.
     *
     * The ticket is minted on the way out rather than fetched by the page,
     * which keeps it off the page itself and means the hotel opens even with
     * scripting turned off.
     */
    public function hotel(Request $request, SessionTicketService $tickets)
    {
        $user = $request->user();

        if (! $user) {
            return view('cms.hotel', ['onlineCount' => $this->onlineCount()]);
        }

        if ($ban = $user->activeBan()) {
            return view('cms.hotel', [
                'onlineCount' => $this->onlineCount(),
                'banned' => $ban,
            ]);
        }

        $world = $tickets->normaliseWorld($request->query('world'));
        $ticket = $tickets->issue($user, $world);

        return redirect()->away(
            $this->clientUrl().'?'.http_build_query(['ticket' => $ticket, 'world' => $world])
        );
    }

    /**
     * Where the game client is served from.
     *
     * The same host as the website by default, so the session, the pictures and
     * the websocket are all same-origin and nothing has to be opened up for
     * them to reach each other.
     */
    private function clientUrl(): string
    {
        return rtrim(config('habnut.client_url', '/client/'), '/').'/';
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
                ->get(['id', 'username', 'rank', 'figure', 'motto']);
        });
    }

    /** A small leaderboard by achievement score. */
    private function topPlayers()
    {
        return Cache::remember('hotel.top_players', 300, function () {
            return User::orderByDesc('achievement_score')
                ->take(5)
                ->get(['id', 'username', 'figure', 'achievement_score']);
        });
    }
}
