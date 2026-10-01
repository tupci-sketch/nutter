<?php

namespace App\Http\Controllers\Dcc;

use App\Http\Controllers\Controller;
use App\Services\AuditService;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class DccGardenController extends Controller
{
    public function __construct(private AuditService $audit) {}

    public function index()
    {
        $activeSeason = DB::table('habnut_garden_seasons')->where('active', true)->first();
        $plotCount = DB::table('habnut_garden_plots')->count();
        $plantCount = DB::table('habnut_garden_plants')->count();

        return view('dcc.garden.index', compact('activeSeason', 'plotCount', 'plantCount'));
    }

    public function startSeason(Request $request)
    {
        $request->validate([
            'name' => ['required', 'string', 'max:100'],
            'ends_at' => ['required', 'date', 'after:now'],
        ]);

        DB::table('habnut_garden_seasons')->where('active', true)->update(['active' => false]);

        $id = DB::table('habnut_garden_seasons')->insertGetId([
            'name' => $request->name,
            'active' => true,
            'started_at' => now(),
            'ends_at' => $request->ends_at,
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        $this->audit->log($request->user()->id, 'garden_season_start', 'garden_season', $id, ['name' => $request->name]);

        return back()->with('success', 'Garden season started.');
    }

    public function plots()
    {
        $plots = DB::table('habnut_garden_plots')
            ->join('habnut_users as users', 'users.id', '=', 'habnut_garden_plots.user_id')
            ->select('habnut_garden_plots.*', 'users.username')
            ->orderByDesc('habnut_garden_plots.updated_at')
            ->paginate(25);

        return view('dcc.garden.plots', compact('plots'));
    }

    public function goals()
    {
        $goals = DB::table('habnut_garden_goals')->orderBy('id')->paginate(25);

        return view('dcc.garden.goals', compact('goals'));
    }
}
