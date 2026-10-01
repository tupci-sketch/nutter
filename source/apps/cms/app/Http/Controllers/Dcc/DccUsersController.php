<?php

namespace App\Http\Controllers\Dcc;

use App\Http\Controllers\Controller;
use App\Models\Ban;
use App\Models\User;
use App\Services\AuditService;
use App\Services\SessionTicketService;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Illuminate\Validation\Rule;

class DccUsersController extends Controller
{
    public function __construct(private AuditService $audit) {}

    public function index(Request $request)
    {
        $query = User::query();

        if ($request->filled('q')) {
            $q = $request->q;
            $query->where(fn ($qb) => $qb->where('username', 'like', "%{$q}%")->orWhere('email', 'like', "%{$q}%"));
        }

        if ($request->filled('rank')) {
            $query->where('rank', $request->rank);
        }

        $users = $query->latest()->paginate(25);

        return view('dcc.users.index', compact('users'));
    }

    public function show(int $id)
    {
        $user = User::findOrFail($id);
        $bans = Ban::where('user_id', $id)->latest()->get();
        $transactions = DB::table('habnut_transactions')->where('user_id', $id)->latest()->take(20)->get();
        $auditLog = DB::table('habnut_audit_logs')->where('target_user_id', $id)->latest()->take(30)->get();

        return view('dcc.users.show', compact('user', 'bans', 'transactions', 'auditLog'));
    }

    public function updateRank(Request $request, int $id)
    {
        $request->validate(['rank' => ['required', 'integer', 'min:1', 'max:9']]);

        $target = User::findOrFail($id);
        $actor = $request->user();

        if ($request->rank >= $actor->rank) {
            return back()->withErrors(['rank' => 'You cannot assign a rank equal to or higher than your own.']);
        }

        $old = $target->rank;
        $target->update(['rank' => $request->rank]);

        $this->audit->log($actor->id, 'rank_change', 'user', $id, ['old' => $old, 'new' => $request->rank]);

        return back()->with('success', 'Rank updated.');
    }

    public function adjustCredits(Request $request, int $id)
    {
        $request->validate(['amount' => ['required', 'integer'], 'reason' => ['required', 'string', 'max:255']]);

        $target = User::findOrFail($id);
        $actor = $request->user();

        DB::table('habnut_transactions')->insert([
            'user_id' => $id,
            'type' => $request->amount > 0 ? 'credit_grant' : 'credit_debit',
            'currency' => 'credits',
            'amount' => abs($request->amount),
            'balance_before' => $target->credits,
            'balance_after' => max(0, $target->credits + $request->amount),
            'description' => 'DCC: '.$request->reason,
            'idempotency_key' => Str::uuid(),
            'transaction_hash' => hash('sha256', $id.$request->amount.microtime()),
            'created_at' => now(),
        ]);

        $target->increment('credits', $request->amount);
        $this->audit->log($actor->id, 'credits_adjust', 'user', $id, ['amount' => $request->amount, 'reason' => $request->reason]);

        return back()->with('success', 'Credits adjusted.');
    }

    public function adjustDiamonds(Request $request, int $id)
    {
        $request->validate(['amount' => ['required', 'integer'], 'reason' => ['required', 'string', 'max:255']]);

        $target = User::findOrFail($id);
        $actor = $request->user();

        DB::table('habnut_transactions')->insert([
            'user_id' => $id,
            'type' => $request->amount > 0 ? 'diamond_grant' : 'diamond_debit',
            'currency' => 'diamonds',
            'amount' => abs($request->amount),
            'balance_before' => $target->diamonds,
            'balance_after' => max(0, $target->diamonds + $request->amount),
            'description' => 'DCC: '.$request->reason,
            'idempotency_key' => Str::uuid(),
            'transaction_hash' => hash('sha256', $id.$request->amount.microtime()),
            'created_at' => now(),
        ]);

        $target->increment('diamonds', $request->amount);
        $this->audit->log($actor->id, 'diamonds_adjust', 'user', $id, ['amount' => $request->amount, 'reason' => $request->reason]);

        return back()->with('success', 'Diamonds adjusted.');
    }

    public function adjustNutPoints(Request $request, int $id)
    {
        $request->validate(['amount' => ['required', 'integer'], 'reason' => ['required', 'string', 'max:255']]);

        $target = User::findOrFail($id);
        $request->user();

        $target->increment('nut_points', $request->amount);

        return back()->with('success', 'Nut Points adjusted.');
    }

    public function ban(Request $request, int $id)
    {
        $request->validate([
            'reason' => ['required', 'string', 'max:1000'],
            'type' => ['required', Rule::in(['permanent', 'temporary'])],
            'expires_at' => ['nullable', 'date', 'after:now', 'required_if:type,temporary'],
        ]);

        $actor = $request->user();
        $target = User::findOrFail($id);

        if ($target->rank >= $actor->rank) {
            return back()->withErrors(['reason' => 'You cannot ban a user with equal or higher rank.']);
        }

        Ban::create([
            'user_id' => $id,
            'staff_id' => $actor->id,
            'reason' => $request->reason,
            'type' => $request->type,
            'active' => true,
            'expires_at' => $request->type === 'temporary' ? $request->expires_at : null,
        ]);

        $this->audit->log($actor->id, 'ban', 'user', $id, ['reason' => $request->reason, 'type' => $request->type]);

        return back()->with('success', 'User banned.');
    }

    public function unban(Request $request, int $id)
    {
        $actor = $request->user();

        Ban::where('user_id', $id)->where('active', true)->update(['active' => false]);

        $this->audit->log($actor->id, 'unban', 'user', $id, []);

        return back()->with('success', 'User unbanned.');
    }

    public function mute(Request $request, int $id)
    {
        $request->validate(['reason' => ['required', 'string'], 'minutes' => ['required', 'integer', 'min:1', 'max:43200']]);

        $actor = $request->user();

        DB::table('habnut_mutes')->updateOrInsert(
            ['user_id' => $id],
            ['reason' => $request->reason, 'expires_at' => now()->addMinutes($request->minutes), 'staff_id' => $actor->id, 'updated_at' => now(), 'created_at' => now()]
        );

        $this->audit->log($actor->id, 'mute', 'user', $id, ['reason' => $request->reason, 'minutes' => $request->minutes]);

        return back()->with('success', 'User muted.');
    }

    public function unmute(Request $request, int $id)
    {
        DB::table('habnut_mutes')->where('user_id', $id)->delete();
        $this->audit->log($request->user()->id, 'unmute', 'user', $id, []);

        return back()->with('success', 'User unmuted.');
    }

    /**
     * Sign in as somebody, to see what they are seeing.
     *
     * Admin only, written to the audit log every time, and the ticket is as
     * short-lived as any other. It is the only way to reproduce a bug a player
     * is reporting from inside their own account.
     */
    public function issueTicket(Request $request, int $id, SessionTicketService $tickets)
    {
        $actor = $request->user();

        if ($actor->rank < User::RANK_ADMIN) {
            abort(403, 'Admin only.');
        }

        $target = User::findOrFail($id);
        $world = $tickets->normaliseWorld($request->input('world'));
        $ticket = $tickets->issue($target, $world);

        $this->audit->log($actor->id, 'ticket_issue', 'user', $id, ['world' => $world]);

        return back()->with('ticket', $ticket);
    }

    public function auditLog(int $id)
    {
        $user = User::findOrFail($id);
        $logs = DB::table('habnut_audit_logs')->where('target_user_id', $id)->latest()->paginate(25);

        return view('dcc.users.audit', compact('user', 'logs'));
    }

    public function transactions(int $id)
    {
        $user = User::findOrFail($id);
        $transactions = DB::table('habnut_transactions')->where('user_id', $id)->latest()->paginate(25);

        return view('dcc.users.transactions', compact('user', 'transactions'));
    }
}
