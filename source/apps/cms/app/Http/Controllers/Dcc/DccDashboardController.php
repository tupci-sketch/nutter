<?php

namespace App\Http\Controllers\Dcc;

use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Support\Facades\DB;

class DccDashboardController extends Controller
{
    public function index()
    {
        $stats = [
            'total_users' => User::count(),
            'online_users' => cache('online_count', 0),
            'total_rooms' => DB::table('habnut_rooms')->count(),
            'open_reports' => DB::table('habnut_reports')->where('status', 'open')->count(),
            'active_bans' => DB::table('habnut_bans')->where('active', true)->count(),
        ];

        $recent_registrations = User::latest()->take(10)->get(['id', 'username', 'email', 'rank', 'member_since']);

        return view('dcc.dashboard', compact('stats', 'recent_registrations'));
    }
}
