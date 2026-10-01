<?php

namespace App\Http\Controllers\Dcc;

use App\Http\Controllers\Controller;
use App\Models\Ban;
use App\Services\AuditService;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class DccModerationController extends Controller
{
    public function __construct(private AuditService $audit) {}

    public function index()
    {
        $openReports = DB::table('habnut_reports')->where('status', 'open')->count();
        $activeBans = Ban::where('active', true)->count();
        $openAppeals = DB::table('habnut_ban_appeals')->where('status', 'pending')->count();

        return view('dcc.moderation.index', compact('openReports', 'activeBans', 'openAppeals'));
    }

    public function reports(Request $request)
    {
        $query = DB::table('habnut_reports')
            ->join('habnut_users as reporter', 'reporter.id', '=', 'habnut_reports.reporter_id')
            ->join('habnut_users as reported', 'reported.id', '=', 'habnut_reports.reported_id')
            ->select('habnut_reports.*', 'reporter.username as reporter_name', 'reported.username as reported_name');

        if ($request->filled('status')) {
            $query->where('habnut_reports.status', $request->status);
        } else {
            $query->where('habnut_reports.status', 'open');
        }

        $reports = $query->latest('habnut_reports.created_at')->paginate(20);

        return view('dcc.moderation.reports', compact('reports'));
    }

    public function report(int $id)
    {
        $report = DB::table('habnut_reports')
            ->join('habnut_users as reporter', 'reporter.id', '=', 'habnut_reports.reporter_id')
            ->join('habnut_users as reported', 'reported.id', '=', 'habnut_reports.reported_id')
            ->select('habnut_reports.*', 'reporter.username as reporter_name', 'reported.username as reported_name')
            ->where('habnut_reports.id', $id)
            ->firstOrFail();

        $chatContext = DB::table('habnut_chat_logs')
            ->where('user_id', $report->reported_id)
            ->where('created_at', '>=', now()->subHour())
            ->latest()
            ->take(20)
            ->get();

        return view('dcc.moderation.report', compact('report', 'chatContext'));
    }

    public function resolve(Request $request, int $id)
    {
        $request->validate([
            'resolution' => ['required', 'string', 'max:1000'],
            'action' => ['required', 'in:none,warn,mute,ban'],
        ]);

        DB::table('habnut_reports')->where('id', $id)->update([
            'status' => 'resolved',
            'resolver_id' => $request->user()->id,
            'resolution' => $request->resolution,
            'resolved_at' => now(),
        ]);

        $this->audit->log($request->user()->id, 'report_resolve', 'report', $id, ['action' => $request->action]);

        return redirect()->route('dcc.moderation.reports')->with('success', 'Report resolved.');
    }

    public function bans(Request $request)
    {
        $query = Ban::with(['user', 'staff'])->where('active', true);

        if ($request->filled('q')) {
            $query->whereHas('user', fn ($q) => $q->where('username', 'like', '%'.$request->q.'%'));
        }

        $bans = $query->latest()->paginate(25);

        return view('dcc.moderation.bans', compact('bans'));
    }

    public function liftBan(Request $request, int $id)
    {
        Ban::where('id', $id)->update(['active' => false]);
        $this->audit->log($request->user()->id, 'ban_lift', 'ban', $id, []);

        return back()->with('success', 'Ban lifted.');
    }

    public function appeals(Request $request)
    {
        $status = $request->input('status', 'pending');
        $appeals = DB::table('habnut_ban_appeals')
            ->join('habnut_users as users', 'users.id', '=', 'habnut_ban_appeals.user_id')
            ->join('habnut_bans', 'habnut_bans.id', '=', 'habnut_ban_appeals.ban_id')
            ->select('habnut_ban_appeals.*', 'users.username')
            ->where('habnut_ban_appeals.status', $status)
            ->latest('habnut_ban_appeals.created_at')
            ->paginate(20);

        return view('dcc.moderation.appeals', compact('appeals', 'status'));
    }

    public function acceptAppeal(Request $request, int $id)
    {
        $appeal = DB::table('habnut_ban_appeals')->where('id', $id)->firstOrFail();
        DB::table('habnut_bans')->where('id', $appeal->ban_id)->update(['active' => false]);
        DB::table('habnut_ban_appeals')->where('id', $id)->update(['status' => 'accepted', 'resolved_at' => now(), 'resolver_id' => $request->user()->id]);
        $this->audit->log($request->user()->id, 'appeal_accept', 'appeal', $id, []);

        return back()->with('success', 'Appeal accepted, ban lifted.');
    }

    public function denyAppeal(Request $request, int $id)
    {
        DB::table('habnut_ban_appeals')->where('id', $id)->update(['status' => 'denied', 'resolved_at' => now(), 'resolver_id' => $request->user()->id]);
        $this->audit->log($request->user()->id, 'appeal_deny', 'appeal', $id, []);

        return back()->with('success', 'Appeal denied.');
    }

    public function wordFilter()
    {
        $words = DB::table('habnut_word_filter')->orderBy('word')->paginate(50);

        return view('dcc.moderation.wordfilter', compact('words'));
    }

    public function addWord(Request $request)
    {
        $request->validate([
            'word' => ['required', 'string', 'max:100'],
            'replacement' => ['nullable', 'string', 'max:100'],
            'severity' => ['required', 'in:low,medium,high'],
        ]);

        DB::table('habnut_word_filter')->insertOrIgnore([
            'word' => strtolower($request->word),
            'replacement' => $request->replacement ?? '****',
            'severity' => $request->severity,
            'created_at' => now(),
        ]);

        return back()->with('success', 'Word added to filter.');
    }

    public function removeWord(Request $request, int $id)
    {
        DB::table('habnut_word_filter')->where('id', $id)->delete();

        return back()->with('success', 'Word removed.');
    }

    public function chatLogs(Request $request)
    {
        $request->validate(['q' => ['nullable', 'string', 'min:3']]);

        $logs = collect();
        if ($request->filled('q')) {
            $logs = DB::table('habnut_chat_logs')
                ->join('habnut_users as users', 'users.id', '=', 'habnut_chat_logs.user_id')
                ->select('habnut_chat_logs.*', 'users.username')
                ->whereFullText('habnut_chat_logs.message', $request->q)
                ->latest('habnut_chat_logs.created_at')
                ->paginate(30);
        }

        return view('dcc.moderation.chatlogs', compact('logs'));
    }
}
