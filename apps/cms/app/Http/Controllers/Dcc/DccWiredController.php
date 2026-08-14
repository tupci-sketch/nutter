<?php

namespace App\Http\Controllers\Dcc;

use App\Http\Controllers\Controller;
use App\Services\AuditService;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class DccWiredController extends Controller
{
    public function __construct(private AuditService $audit) {}

    public function index()
    {
        $globalVarCount = DB::table('habnut_wired_variables')->where('scope', 'global')->count();
        $execLogCount   = DB::table('habnut_wired_execution_log')->where('created_at', '>=', now()->subDay())->count();

        return view('dcc.wired.index', compact('globalVarCount', 'execLogCount'));
    }

    public function globalVars()
    {
        $vars = DB::table('habnut_wired_variables')
            ->where('scope', 'global')
            ->orderBy('var_key')
            ->paginate(30);

        return view('dcc.wired.vars', compact('vars'));
    }

    public function updateVar(Request $request, string $key)
    {
        $request->validate(['value' => ['required', 'string', 'max:1000']]);

        DB::table('habnut_wired_variables')
            ->where('scope', 'global')
            ->where('var_key', $key)
            ->update(['var_value' => $request->value, 'updated_at' => now()]);

        $this->audit->log($request->user()->id, 'wired_var_update', 'wired_var', 0, ['key' => $key]);

        return back()->with('success', 'Variable updated.');
    }

    public function deleteVar(Request $request, string $key)
    {
        DB::table('habnut_wired_variables')
            ->where('scope', 'global')
            ->where('var_key', $key)
            ->delete();

        $this->audit->log($request->user()->id, 'wired_var_delete', 'wired_var', 0, ['key' => $key]);

        return back()->with('success', 'Variable deleted.');
    }

    public function execLog(Request $request)
    {
        $logs = DB::table('habnut_wired_execution_log')
            ->orderByDesc('created_at')
            ->paginate(50);

        return view('dcc.wired.execlog', compact('logs'));
    }
}
