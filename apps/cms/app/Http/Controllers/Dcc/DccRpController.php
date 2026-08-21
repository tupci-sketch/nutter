<?php

namespace App\Http\Controllers\Dcc;

use App\Http\Controllers\Controller;
use App\Services\AuditService;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class DccRpController extends Controller
{
    public function __construct(private AuditService $audit) {}

    public function index()
    {
        $charCount = DB::table('habnut_rp_characters')->count();
        $imprisoned = DB::table('habnut_rp_characters')->whereNotNull('prison_expiry')->where('prison_expiry', '>', now())->count();
        $activeDispatch = DB::table('habnut_rp_dispatch_calls')->where('status', 'pending')->count();

        return view('dcc.rp.index', compact('charCount', 'imprisoned', 'activeDispatch'));
    }

    public function characters(Request $request)
    {
        $query = DB::table('habnut_rp_characters')
            ->join('users', 'users.id', '=', 'habnut_rp_characters.user_id')
            ->select('habnut_rp_characters.*', 'users.username');

        if ($request->filled('q')) {
            $q = $request->q;
            $query->where(fn ($qb) => $qb->where('habnut_rp_characters.name', 'like', "%{$q}%")->orWhere('habnut_rp_characters.surname', 'like', "%{$q}%"));
        }

        $characters = $query->orderBy('habnut_rp_characters.name')->paginate(25);

        return view('dcc.rp.characters', compact('characters'));
    }

    public function character(int $id)
    {
        $character = DB::table('habnut_rp_characters')
            ->join('users', 'users.id', '=', 'habnut_rp_characters.user_id')
            ->select('habnut_rp_characters.*', 'users.username')
            ->where('habnut_rp_characters.id', $id)
            ->firstOrFail();

        $crimes = DB::table('habnut_rp_crimes')->where('character_id', $id)->latest()->get();
        $bankTx = DB::table('habnut_rp_bank_transactions')
            ->where('from_character_id', $id)->orWhere('to_character_id', $id)
            ->latest()->take(20)->get();

        return view('dcc.rp.character', compact('character', 'crimes', 'bankTx'));
    }

    public function pardon(Request $request, int $id)
    {
        DB::table('habnut_rp_characters')->where('id', $id)->update(['prison_expiry' => null]);
        DB::table('habnut_rp_prison')->where('character_id', $id)->where('released_at', null)->update(['released_at' => now(), 'release_reason' => 'DCC pardon']);

        $this->audit->log($request->user()->id, 'rp_pardon', 'rp_character', $id, []);

        return back()->with('success', 'Character pardoned.');
    }

    public function factions()
    {
        $factions = DB::table('habnut_rp_factions')->orderBy('name')->paginate(25);

        return view('dcc.rp.factions', compact('factions'));
    }

    public function court()
    {
        $cases = DB::table('habnut_rp_court_cases')
            ->join('habnut_rp_crimes', 'habnut_rp_crimes.id', '=', 'habnut_rp_court_cases.crime_id')
            ->join('habnut_rp_characters as def', 'def.id', '=', 'habnut_rp_court_cases.defendant_id')
            ->select('habnut_rp_court_cases.*', 'def.name as defendant_name', 'habnut_rp_crimes.type as crime_type')
            ->orderByDesc('habnut_rp_court_cases.opened_at')
            ->paginate(25);

        return view('dcc.rp.court', compact('cases'));
    }

    public function bank()
    {
        $transactions = DB::table('habnut_rp_bank_transactions')
            ->orderByDesc('created_at')
            ->paginate(30);

        return view('dcc.rp.bank', compact('transactions'));
    }

    public function laws()
    {
        $laws = DB::table('habnut_rp_laws')->where('active', true)->orderBy('title')->paginate(25);

        return view('dcc.rp.laws', compact('laws'));
    }

    public function createLaw(Request $request)
    {
        $request->validate([
            'title' => ['required', 'string', 'max:200'],
            'body' => ['required', 'string'],
        ]);

        $id = DB::table('habnut_rp_laws')->insertGetId([
            'title' => $request->title,
            'body' => $request->body,
            'active' => true,
            'enacted_at' => now(),
            'created_at' => now(),
        ]);

        $this->audit->log($request->user()->id, 'rp_law_create', 'rp_law', $id, ['title' => $request->title]);

        return back()->with('success', 'Law enacted.');
    }

    public function repealLaw(Request $request, int $id)
    {
        DB::table('habnut_rp_laws')->where('id', $id)->update(['active' => false, 'repealed_at' => now()]);
        $this->audit->log($request->user()->id, 'rp_law_repeal', 'rp_law', $id, []);

        return back()->with('success', 'Law repealed.');
    }
}
