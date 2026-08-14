<?php

namespace App\Http\Controllers\Dcc;

use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Support\Facades\DB;

class DccStaffController extends Controller
{
    public function index()
    {
        $staffByRank = User::where('rank', '>=', 4)
            ->orderByDesc('rank')
            ->get(['id', 'username', 'rank', 'last_login']);

        return view('dcc.staff.index', compact('staffByRank'));
    }

    public function online()
    {
        $online = cache()->get('online_staff', []);
        return view('dcc.staff.online', compact('online'));
    }

    public function actions()
    {
        $actions = DB::table('habnut_audit_logs')
            ->join('users', 'users.id', '=', 'habnut_audit_logs.actor_user_id')
            ->select('habnut_audit_logs.*', 'users.username as actor_name')
            ->where('users.rank', '>=', 4)
            ->latest('habnut_audit_logs.created_at')
            ->paginate(30);

        return view('dcc.staff.actions', compact('actions'));
    }

    public function commands()
    {
        $commands = DB::table('habnut_audit_logs')
            ->join('users', 'users.id', '=', 'habnut_audit_logs.actor_user_id')
            ->select('habnut_audit_logs.*', 'users.username as actor_name')
            ->where('habnut_audit_logs.action', 'like', 'cmd_%')
            ->latest('habnut_audit_logs.created_at')
            ->paginate(30);

        return view('dcc.staff.commands', compact('commands'));
    }
}
