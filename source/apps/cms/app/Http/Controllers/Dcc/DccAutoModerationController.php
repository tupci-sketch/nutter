<?php

namespace App\Http\Controllers\Dcc;

use App\Http\Controllers\Controller;
use App\Services\AuditService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\Rule;

/**
 * The review queue behind the automatic mutes.
 *
 * A message the content policy stops silences its sender immediately, because a
 * threat left standing while somebody waits for a moderator has already done its
 * damage. That is only defensible if a person actually looks afterwards, which
 * is what this page is for.
 */
class DccAutoModerationController extends Controller
{
    private const CATEGORIES = ['hate', 'threat', 'minor_safety', 'doxxing', 'self_harm', 'scam'];

    public function __construct(private AuditService $audit) {}

    // ─── the queue ──────────────────────────────────────────────────────────

    public function index(Request $request)
    {
        $status = $request->query('status', 'pending_review');

        $cases = DB::table('habnut_auto_mutes as c')
            ->leftJoin('habnut_users as u', 'u.id', '=', 'c.user_id')
            ->leftJoin('habnut_content_rules as r', 'r.id', '=', 'c.rule_id')
            ->leftJoin('habnut_mutes as m', 'm.id', '=', 'c.mute_id')
            ->leftJoin('habnut_users as s', 's.id', '=', 'c.reviewed_by_id')
            // Named explicitly: without a select list the joined tables' own id
            // columns overwrite the case's, and every row then links to the
            // wrong case.
            ->select([
                'c.id', 'c.user_id', 'c.mute_id', 'c.category', 'c.message', 'c.room_id',
                'c.status', 'c.review_notes', 'c.reviewed_at', 'c.created_at',
                'u.username', 'r.label', 'm.expires_at',
                's.username as reviewer',
            ])
            ->when(in_array($status, ['pending_review', 'upheld', 'overturned'], true),
                fn ($q) => $q->where('c.status', $status))
            // The worst categories first, then oldest, so the queue is worked in
            // the order that matters rather than the order it arrived.
            ->orderByRaw("CASE c.category WHEN 'minor_safety' THEN 1 WHEN 'hate' THEN 2 "
                ."WHEN 'threat' THEN 3 WHEN 'self_harm' THEN 4 WHEN 'scam' THEN 5 ELSE 6 END")
            ->orderBy('c.created_at')
            ->paginate(25)
            ->withQueryString()
            ->through(function ($case) {
                $case->help_requests = DB::table('habnut_mute_help_requests')
                    ->where('auto_mute_id', $case->id)
                    ->orderByDesc('created_at')
                    ->get();

                return $case;
            });

        return view('dcc.automod.index', [
            'cases' => $cases,
            'status' => $status,
            'pending' => DB::table('habnut_auto_mutes')->where('status', 'pending_review')->count(),
            'waitingOnHelp' => DB::table('habnut_mute_help_requests')->whereNull('handled_at')->count(),
        ]);
    }

    /**
     * Settles a case.
     *
     * Overturning lifts the mute with it. A decision in the player's favour that
     * leaves them silenced has not decided anything.
     */
    public function review(Request $request, int $id): RedirectResponse
    {
        $data = $request->validate([
            'decision' => ['required', 'in:uphold,overturn'],
            'notes' => ['nullable', 'string', 'max:512'],
        ]);

        $case = DB::table('habnut_auto_mutes')->where('id', $id)->first();
        abort_if($case === null, 404);

        if ($case->status !== 'pending_review') {
            return back()->with('error', 'That case has already been settled.');
        }

        $upheld = $data['decision'] === 'uphold';

        DB::transaction(function () use ($case, $upheld, $data, $request, $id) {
            DB::table('habnut_auto_mutes')->where('id', $id)->update([
                'status' => $upheld ? 'upheld' : 'overturned',
                'reviewed_by_id' => $request->user()->id,
                'reviewed_at' => now(),
                'review_notes' => $data['notes'] ?? null,
            ]);

            if (! $upheld && $case->mute_id !== null) {
                DB::table('habnut_mutes')
                    ->where('id', $case->mute_id)
                    ->whereNull('lifted_at')
                    ->update(['lifted_at' => now(), 'lifted_by_id' => $request->user()->id]);
            }

            // Whoever asked for a person to look has now had one.
            DB::table('habnut_mute_help_requests')
                ->where('auto_mute_id', $id)
                ->whereNull('handled_at')
                ->update([
                    'handled_by_id' => $request->user()->id,
                    'handled_at' => now(),
                    'response' => $data['notes'] ?? null,
                ]);
        });

        $this->audit->log($request->user()->id, 'automute_'.($upheld ? 'upheld' : 'overturned'),
            'user', (int) $case->user_id, ['case' => $id, 'category' => $case->category]);

        return back()->with('success', $upheld ? 'Mute upheld.' : 'Mute lifted.');
    }

    // ─── the rules ──────────────────────────────────────────────────────────

    public function rules()
    {
        return view('dcc.automod.rules', [
            'rules' => DB::table('habnut_content_rules')
                ->orderByDesc('severity')->orderBy('category')->orderBy('id')->get(),
            'categories' => self::CATEGORIES,
        ]);
    }

    public function storeRule(Request $request): RedirectResponse
    {
        $data = $this->validateRule($request);

        $id = DB::table('habnut_content_rules')->insertGetId($data + [
            'created_by_id' => $request->user()->id,
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        $this->audit->log($request->user()->id, 'content_rule_create', 'content_rule', $id, $data);

        return back()->with('success', 'Rule added. It takes effect on the next reload.');
    }

    public function updateRule(Request $request, int $id): RedirectResponse
    {
        abort_unless(DB::table('habnut_content_rules')->where('id', $id)->exists(), 404);

        $data = $this->validateRule($request);
        DB::table('habnut_content_rules')->where('id', $id)
            ->update($data + ['updated_at' => now()]);

        $this->audit->log($request->user()->id, 'content_rule_update', 'content_rule', $id, $data);

        return back()->with('success', 'Rule updated.');
    }

    public function toggleRule(Request $request, int $id): RedirectResponse
    {
        $rule = DB::table('habnut_content_rules')->where('id', $id)->first();
        abort_if($rule === null, 404);

        DB::table('habnut_content_rules')->where('id', $id)
            ->update(['enabled' => ! $rule->enabled, 'updated_at' => now()]);

        $this->audit->log($request->user()->id, 'content_rule_toggle', 'content_rule', $id,
            ['enabled' => ! $rule->enabled]);

        return back()->with('success', $rule->enabled ? 'Rule switched off.' : 'Rule switched on.');
    }

    /**
     * Checks a rule before it is stored.
     *
     * The pattern is compiled here rather than trusted, because a rule that will
     * not compile is skipped at load time and the hotel then runs with one fewer
     * protection than the person who wrote it believes.
     */
    private function validateRule(Request $request): array
    {
        $data = $request->validate([
            'category' => ['required', Rule::in(self::CATEGORIES)],
            'label' => ['required', 'string', 'max:128'],
            'pattern' => ['required', 'string', 'max:512'],
            'exempt_pattern' => ['nullable', 'string', 'max:512'],
            'match_mode' => ['required', 'in:words,condensed,both'],
            'action' => ['required', 'in:mute,flag'],
            'severity' => ['required', 'integer', 'between:1,5'],
            'mute_minutes' => ['required', 'integer', 'between:5,20160'],
        ]);

        foreach (['pattern', 'exempt_pattern'] as $field) {
            $expression = $data[$field] ?? null;
            if ($expression === null || $expression === '') {
                continue;
            }
            if (str_contains($expression, '\\')) {
                abort(422, 'Patterns must not contain a backslash: a backslash means '
                    .'different things to different databases. Use [0-9] for a digit, '
                    .'[ ] for a space, and (?<![a-z]) or (?![a-z]) for a word edge.');
            }
            if (@preg_match('/'.str_replace('/', '\\/', $expression).'/i', '') === false) {
                abort(422, 'That '.str_replace('_', ' ', $field).' is not a valid expression.');
            }
        }

        return $data;
    }
}
