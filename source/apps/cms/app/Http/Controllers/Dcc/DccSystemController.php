<?php

namespace App\Http\Controllers\Dcc;

use App\Http\Controllers\Controller;
use App\Services\AuditService;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class DccSystemController extends Controller
{
    public function __construct(private AuditService $audit) {}

    public function index()
    {
        $flagCount = DB::table('habnut_feature_flags')->count();
        $settingCount = DB::table('habnut_system_settings')->count();
        $maintenance = file_exists(base_path('.maintenance'));

        return view('dcc.system.index', compact('flagCount', 'settingCount', 'maintenance'));
    }

    public function settings()
    {
        $settings = DB::table('habnut_system_settings')->orderBy('setting_key')->paginate(50);

        return view('dcc.system.settings', compact('settings'));
    }

    public function updateSettings(Request $request)
    {
        $request->validate([
            'key' => ['required', 'string', 'max:100'],
            'value' => ['required', 'string', 'max:1000'],
        ]);

        DB::table('habnut_system_settings')->updateOrInsert(
            ['setting_key' => $request->key],
            ['setting_value' => $request->value, 'updated_at' => now()]
        );

        $this->audit->log($request->user()->id, 'setting_update', 'system', 0, ['key' => $request->key]);

        return back()->with('success', 'Setting updated.');
    }

    public function flags()
    {
        $flags = DB::table('habnut_feature_flags')->orderBy('flag_key')->paginate(50);

        return view('dcc.system.flags', compact('flags'));
    }

    public function toggleFlag(Request $request, string $key)
    {
        $flag = DB::table('habnut_feature_flags')->where('flag_key', $key)->first();
        $newVal = ! $flag->enabled;

        DB::table('habnut_feature_flags')->where('flag_key', $key)->update([
            'enabled' => $newVal,
            'updated_at' => now(),
        ]);

        $this->audit->log($request->user()->id, 'flag_toggle', 'feature_flag', 0, ['key' => $key, 'enabled' => $newVal]);

        return back()->with('success', 'Flag toggled.');
    }

    public function monitoring()
    {
        $grafanaUrl = config('habnut.grafana_url', 'http://localhost:3000');

        return view('dcc.system.monitoring', compact('grafanaUrl'));
    }

    public function auditLog(Request $request)
    {
        $query = DB::table('habnut_audit_logs')
            ->join('users', 'users.id', '=', 'habnut_audit_logs.actor_user_id')
            ->select('habnut_audit_logs.*', 'users.username as actor_name');

        if ($request->filled('action')) {
            $query->where('habnut_audit_logs.action', 'like', '%'.$request->action.'%');
        }

        $logs = $query->latest('habnut_audit_logs.created_at')->paginate(30);

        return view('dcc.system.audit', compact('logs'));
    }

    public function maintenance()
    {
        $active = file_exists(base_path('.maintenance'));

        return view('dcc.system.maintenance', compact('active'));
    }

    public function enableMaintenance(Request $request)
    {
        file_put_contents(base_path('.maintenance'), json_encode([
            'message' => $request->input('message', 'Habnut is currently undergoing maintenance.'),
            'enabled_by' => $request->user()->username,
            'enabled_at' => now()->toIso8601String(),
        ]));

        $this->audit->log($request->user()->id, 'maintenance_enable', 'system', 0, []);

        return back()->with('success', 'Maintenance mode enabled.');
    }

    public function disableMaintenance(Request $request)
    {
        if (file_exists(base_path('.maintenance'))) {
            unlink(base_path('.maintenance'));
        }

        $this->audit->log($request->user()->id, 'maintenance_disable', 'system', 0, []);

        return back()->with('success', 'Maintenance mode disabled.');
    }
}
