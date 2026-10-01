<?php

namespace App\Http\Controllers\Dcc;

use App\Http\Controllers\Controller;
use App\Models\ForumCategory;
use App\Models\ForumModerator;
use App\Models\ForumPost;
use App\Models\ForumReport;
use App\Services\AuditService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\Rule;

/**
 * Staff administration of the forums: the boards themselves, who runs them, and
 * the queue of posts players have reported.
 */
class DccForumController extends Controller
{
    public function __construct(private AuditService $audit) {}

    // ─── boards ─────────────────────────────────────────────────────────────

    public function index()
    {
        return view('dcc.forum.index', [
            'categories' => ForumCategory::orderBy('sort_order')->orderBy('id')->get(),
            'openReports' => ForumReport::where('status', 'open')->count(),
            'moderators' => ForumModerator::with('user:id,username')->get(),
        ]);
    }

    public function storeCategory(Request $request): RedirectResponse
    {
        $data = $request->validate([
            'name' => ['required', 'string', 'max:96'],
            'slug' => ['required', 'string', 'max:64', 'regex:/^[a-z0-9-]+$/',
                Rule::unique('habnut_forum_categories', 'slug')],
            'description' => ['nullable', 'string', 'max:512'],
            'min_read_rank' => ['required', 'integer', 'between:0,7'],
            'min_post_rank' => ['required', 'integer', 'between:0,7'],
            'sort_order' => ['required', 'integer', 'between:0,9999'],
        ]);

        $category = ForumCategory::create($data + ['description' => $data['description'] ?? '']);
        $this->audit->log($request->user()->id, 'forum_category_create', 'forum_category',
            $category->id, ['slug' => $category->slug]);

        return back()->with('success', 'Board created.');
    }

    public function updateCategory(Request $request, int $id): RedirectResponse
    {
        $category = ForumCategory::findOrFail($id);

        $data = $request->validate([
            'name' => ['required', 'string', 'max:96'],
            'description' => ['nullable', 'string', 'max:512'],
            'min_read_rank' => ['required', 'integer', 'between:0,7'],
            'min_post_rank' => ['required', 'integer', 'between:0,7'],
            'sort_order' => ['required', 'integer', 'between:0,9999'],
            'locked' => ['nullable', 'boolean'],
        ]);

        $category->update([
            'name' => $data['name'],
            'description' => $data['description'] ?? '',
            'min_read_rank' => $data['min_read_rank'],
            'min_post_rank' => $data['min_post_rank'],
            'sort_order' => $data['sort_order'],
            'locked' => (bool) ($data['locked'] ?? false),
        ]);

        $this->audit->log($request->user()->id, 'forum_category_update', 'forum_category',
            $category->id, $data);

        return back()->with('success', 'Board updated.');
    }

    // ─── forum staff ────────────────────────────────────────────────────────

    /**
     * Appoints somebody to run a board.
     *
     * Forum roles are separate from hotel rank on purpose, so this is how a
     * player who is trusted with a board — and nothing else — gets that trust.
     */
    public function grantRole(Request $request): RedirectResponse
    {
        $data = $request->validate([
            'username' => ['required', 'string', 'exists:habnut_users,username'],
            'scope' => ['required', Rule::in([
                ForumModerator::SCOPE_GLOBAL,
                ForumModerator::SCOPE_CATEGORY,
                ForumModerator::SCOPE_GROUP,
            ])],
            'scope_id' => ['nullable', 'integer'],
            'role' => ['required', Rule::in([
                ForumModerator::ROLE_MODERATOR,
                ForumModerator::ROLE_ADMINISTRATOR,
            ])],
        ]);

        // A scoped role without a scope would silently become a global one.
        if ($data['scope'] !== ForumModerator::SCOPE_GLOBAL && empty($data['scope_id'])) {
            return back()->withErrors(['scope_id' => 'Choose which board or group this covers.']);
        }

        $userId = DB::table('habnut_users as users')->where('username', $data['username'])->value('id');

        ForumModerator::updateOrCreate(
            [
                'user_id' => $userId,
                'scope' => $data['scope'],
                'scope_id' => $data['scope'] === ForumModerator::SCOPE_GLOBAL ? null : $data['scope_id'],
            ],
            ['role' => $data['role'], 'granted_by_id' => $request->user()->id]
        );

        $this->audit->log($request->user()->id, 'forum_role_grant', 'user', $userId, $data);

        return back()->with('success', $data['username'].' now holds that role.');
    }

    public function revokeRole(Request $request, int $id): RedirectResponse
    {
        $role = ForumModerator::findOrFail($id);
        $role->delete();

        $this->audit->log($request->user()->id, 'forum_role_revoke', 'user', $role->user_id, [
            'scope' => $role->scope,
            'scope_id' => $role->scope_id,
        ]);

        return back()->with('success', 'Role removed.');
    }

    // ─── reports ────────────────────────────────────────────────────────────

    public function reports(Request $request)
    {
        $status = $request->query('status', 'open');

        $reports = ForumReport::with([
            'reporter:id,username',
            'post:id,thread_id,author_id,body,hidden',
            'post.author:id,username',
            'post.thread:id,title',
        ])
            ->when(in_array($status, ['open', 'upheld', 'dismissed'], true),
                fn ($q) => $q->where('status', $status))
            ->orderByDesc('created_at')
            ->paginate(25)
            ->withQueryString();

        return view('dcc.forum.reports', compact('reports', 'status'));
    }

    /**
     * Settles a report.
     *
     * Upholding it hides the post as well, because a report agreed with that
     * leaves the post up has not actually been acted on.
     */
    public function resolveReport(Request $request, int $id): RedirectResponse
    {
        $report = ForumReport::with('post')->findOrFail($id);

        $data = $request->validate([
            'decision' => ['required', 'in:uphold,dismiss'],
            'notes' => ['nullable', 'string', 'max:512'],
        ]);

        DB::transaction(function () use ($report, $data, $request) {
            $report->forceFill([
                'status' => $data['decision'] === 'uphold' ? 'upheld' : 'dismissed',
                'handled_by_id' => $request->user()->id,
                'handled_at' => now(),
                'notes' => $data['notes'] ?? null,
            ])->save();

            if ($data['decision'] === 'uphold' && $report->post !== null) {
                $report->post->forceFill([
                    'hidden' => true,
                    'hidden_at' => now(),
                    'hidden_by_id' => $request->user()->id,
                    'hidden_reason' => $data['notes'] ?? null ?: 'Upheld report',
                ])->save();
            }
        });

        $this->audit->log($request->user()->id, 'forum_report_'.$data['decision'],
            'forum_post', $report->post_id, ['report' => $report->id]);

        return back()->with('success', 'Report settled.');
    }

    /** Restores a post a moderator hid by mistake. */
    public function restorePost(Request $request, int $id): RedirectResponse
    {
        $post = ForumPost::findOrFail($id);
        $post->forceFill([
            'hidden' => false,
            'hidden_at' => null,
            'hidden_by_id' => null,
            'hidden_reason' => null,
        ])->save();

        $this->audit->log($request->user()->id, 'forum_post_restore', 'forum_post', $post->id, []);

        return back()->with('success', 'Post restored.');
    }
}
