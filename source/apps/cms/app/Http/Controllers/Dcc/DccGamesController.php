<?php

namespace App\Http\Controllers\Dcc;

use App\Http\Controllers\Controller;
use Illuminate\Support\Facades\DB;

class DccGamesController extends Controller
{
    public function index()
    {
        $matchCount = DB::table('habnut_game_matches')->count();
        $tournamentCount = DB::table('habnut_tournaments')->count();
        $activeTournaments = DB::table('habnut_tournaments')->where('status', 'active')->count();

        return view('dcc.games.index', compact('matchCount', 'tournamentCount', 'activeTournaments'));
    }

    public function matches()
    {
        $matches = DB::table('habnut_game_matches')
            ->orderByDesc('started_at')
            ->paginate(25);

        return view('dcc.games.matches', compact('matches'));
    }

    public function leaderboards()
    {
        $leaderboards = DB::table('habnut_leaderboards')
            ->join('users', 'users.id', '=', 'habnut_leaderboards.user_id')
            ->select('habnut_leaderboards.*', 'users.username')
            ->orderByDesc('score')
            ->paginate(25);

        return view('dcc.games.leaderboards', compact('leaderboards'));
    }

    public function tournaments()
    {
        $tournaments = DB::table('habnut_tournaments')
            ->orderByDesc('created_at')
            ->paginate(25);

        return view('dcc.games.tournaments', compact('tournaments'));
    }
}
