<?php

namespace App\Http\Controllers\Dcc;

use App\Http\Controllers\Controller;
use App\Models\User;
use App\Services\AuditService;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Illuminate\Validation\Rule;

class DccEconomyController extends Controller
{
    public function __construct(private AuditService $audit) {}

    public function index()
    {
        $totals = DB::table('users')->selectRaw(
            'SUM(credits) as total_credits, SUM(diamonds) as total_diamonds, SUM(nut_points) as total_nutpoints, COUNT(*) as users'
        )->first();

        $volume24h = DB::table('habnut_transactions')
            ->where('created_at', '>=', now()->subDay())
            ->selectRaw('currency, SUM(amount) as vol')
            ->groupBy('currency')
            ->pluck('vol', 'currency');

        return view('dcc.economy.index', compact('totals', 'volume24h'));
    }

    public function transactions(Request $request)
    {
        $query = DB::table('habnut_transactions')
            ->join('users', 'users.id', '=', 'habnut_transactions.user_id')
            ->select('habnut_transactions.*', 'users.username');

        if ($request->filled('currency')) {
            $query->where('currency', $request->currency);
        }

        if ($request->filled('type')) {
            $query->where('habnut_transactions.type', $request->type);
        }

        $transactions = $query->latest('habnut_transactions.created_at')->paginate(30);

        return view('dcc.economy.transactions', compact('transactions'));
    }

    public function grant(Request $request)
    {
        $request->validate([
            'user_id' => ['required', 'exists:users,id'],
            'currency' => ['required', Rule::in(['credits', 'diamonds', 'nut_points', 'seasonal_currency'])],
            'amount' => ['required', 'integer', 'min:1'],
            'reason' => ['required', 'string', 'max:500'],
        ]);

        $user = User::findOrFail($request->user_id);
        $column = $request->currency;
        $before = $user->{$column};

        DB::table('habnut_transactions')->insert([
            'user_id' => $user->id,
            'type' => $request->currency.'_grant',
            'currency' => $request->currency,
            'amount' => $request->amount,
            'balance_before' => $before,
            'balance_after' => $before + $request->amount,
            'description' => 'DCC grant: '.$request->reason,
            'idempotency_key' => Str::uuid(),
            'transaction_hash' => hash('sha256', $user->id.$request->amount.microtime()),
            'created_at' => now(),
        ]);

        $user->increment($column, $request->amount);
        $this->audit->log($request->user()->id, 'economy_grant', 'user', $user->id, [
            'currency' => $request->currency,
            'amount' => $request->amount,
            'reason' => $request->reason,
        ]);

        return back()->with('success', 'Currency granted.');
    }

    public function debit(Request $request)
    {
        $request->validate([
            'user_id' => ['required', 'exists:users,id'],
            'currency' => ['required', Rule::in(['credits', 'diamonds', 'nut_points', 'seasonal_currency'])],
            'amount' => ['required', 'integer', 'min:1'],
            'reason' => ['required', 'string', 'max:500'],
        ]);

        $user = User::findOrFail($request->user_id);
        $column = $request->currency;
        $before = $user->{$column};
        $after = max(0, $before - $request->amount);

        DB::table('habnut_transactions')->insert([
            'user_id' => $user->id,
            'type' => $request->currency.'_debit',
            'currency' => $request->currency,
            'amount' => $request->amount,
            'balance_before' => $before,
            'balance_after' => $after,
            'description' => 'DCC debit: '.$request->reason,
            'idempotency_key' => Str::uuid(),
            'transaction_hash' => hash('sha256', $user->id.$request->amount.microtime()),
            'created_at' => now(),
        ]);

        $user->update([$column => $after]);
        $this->audit->log($request->user()->id, 'economy_debit', 'user', $user->id, [
            'currency' => $request->currency,
            'amount' => $request->amount,
            'reason' => $request->reason,
        ]);

        return back()->with('success', 'Currency debited.');
    }

    public function stats()
    {
        $daily = DB::table('habnut_transactions')
            ->selectRaw('DATE(created_at) as d, currency, SUM(amount) as vol')
            ->where('created_at', '>=', now()->subDays(30))
            ->groupByRaw('DATE(created_at), currency')
            ->orderBy('d')
            ->get();

        return view('dcc.economy.stats', compact('daily'));
    }
}
