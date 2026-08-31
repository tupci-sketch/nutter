<?php

namespace App\Http\Controllers\Cms;

use App\Http\Controllers\Controller;
use App\Models\ForumCategory;
use App\Models\ForumPost;
use App\Models\ForumReport;
use App\Models\ForumThread;
use App\Support\ForumAuthority;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Symfony\Component\HttpKernel\Exception\AccessDeniedHttpException;

/**
 * The forums: public boards and group boards.
 *
 * Every permission question goes through ForumAuthority so the page and the
 * request that follows it agree about what a player may do.
 */
class ForumController extends Controller
{
    private const THREADS_PER_PAGE = 20;

    private const POSTS_PER_PAGE = 15;

    public function __construct(private readonly ForumAuthority $authority) {}

    /** The board list. */
    public function index(Request $request)
    {
        $user = $request->user();

        $categories = ForumCategory::visibleTo($user)->get();
        $categories->each(fn (ForumCategory $c) => $c->setAttribute(
            'can_post', $this->authority->canStartThread($user, $c)
        ));

        return view('cms.forum.index', [
            'categories' => $categories,
            'latest' => $this->latestThreads($user),
        ]);
    }

    /** One board's threads. */
    public function category(Request $request, string $slug)
    {
        $category = ForumCategory::where('slug', $slug)->firstOrFail();
        $this->assert($this->authority->canReadCategory($request->user(), $category));

        $threads = $category->threads()
            ->listed()
            ->with(['author:id,username,look', 'lastPoster:id,username'])
            ->paginate(self::THREADS_PER_PAGE);

        return view('cms.forum.category', [
            'category' => $category,
            'threads' => $threads,
            'canPost' => $this->authority->canStartThread($request->user(), $category),
            'canModerate' => $this->authority->canModerateCategory($request->user(), $category),
        ]);
    }

    /** One thread's posts. */
    public function thread(Request $request, int $id)
    {
        $thread = ForumThread::with('category')->findOrFail($id);
        $user = $request->user();
        $this->assert($this->authority->canReadThread($user, $thread));

        $canModerate = $this->authority->canModerateThread($user, $thread);

        // Moderators see hidden posts, with the reason, so they can undo a
        // mistake without going to the database for it.
        $posts = $thread->posts()
            ->when(! $canModerate, fn ($q) => $q->where('hidden', false))
            ->with(['author:id,username,look,motto,rank,created_at', 'editor:id,username'])
            ->orderBy('created_at')
            ->paginate(self::POSTS_PER_PAGE);

        $thread->increment('views');

        return view('cms.forum.thread', [
            'thread' => $thread,
            'posts' => $posts,
            'canReply' => $this->authority->canReply($user, $thread),
            'canModerate' => $canModerate,
            'isAdministrator' => $this->authority->isForumAdministrator($user, $thread),
        ]);
    }

    /** A group's forum. */
    public function groupForum(Request $request, int $groupId)
    {
        $group = DB::table('habnut_groups')->where('id', $groupId)->first();
        abort_if($group === null, 404);

        $user = $request->user();
        $this->assert($this->authority->canReadGroupForum($user, $groupId));

        $threads = ForumThread::where('group_id', $groupId)
            ->listed()
            ->with(['author:id,username,look', 'lastPoster:id,username'])
            ->paginate(self::THREADS_PER_PAGE);

        return view('cms.forum.group', [
            'group' => $group,
            'threads' => $threads,
            'canPost' => $this->authority->canPostInGroupForum($user, $groupId),
        ]);
    }

    /** Starts a thread in a group's forum. */
    public function storeGroupThread(Request $request, int $groupId): RedirectResponse
    {
        abort_unless(DB::table('habnut_groups')->where('id', $groupId)->exists(), 404);
        $this->assert($this->authority->canPostInGroupForum($request->user(), $groupId));

        $data = $request->validate([
            'title' => ['required', 'string', 'min:3', 'max:255'],
            'body' => ['required', 'string', 'min:2', 'max:20000'],
        ]);

        $thread = DB::transaction(function () use ($groupId, $request, $data) {
            $thread = ForumThread::create([
                'group_id' => $groupId,
                'author_id' => $request->user()->id,
                'title' => $data['title'],
            ]);

            $this->appendPost($thread, $request->user()->id, $data['body']);

            return $thread;
        });

        return redirect()->route('forum.thread', $thread->id)
            ->with('success', 'Thread posted.');
    }

    // ─── writing ────────────────────────────────────────────────────────────

    /** Starts a thread with its opening post. */
    public function storeThread(Request $request, string $slug): RedirectResponse
    {
        $category = ForumCategory::where('slug', $slug)->firstOrFail();
        $this->assert($this->authority->canStartThread($request->user(), $category));

        $data = $request->validate([
            'title' => ['required', 'string', 'min:3', 'max:255'],
            'body' => ['required', 'string', 'min:2', 'max:20000'],
        ]);

        $thread = DB::transaction(function () use ($category, $request, $data) {
            $thread = ForumThread::create([
                'category_id' => $category->id,
                'author_id' => $request->user()->id,
                'title' => $data['title'],
            ]);

            $this->appendPost($thread, $request->user()->id, $data['body']);
            $category->increment('thread_count');

            return $thread;
        });

        return redirect()->route('forum.thread', $thread->id)
            ->with('success', 'Thread posted.');
    }

    /** Adds a reply. */
    public function storePost(Request $request, int $id): RedirectResponse
    {
        $thread = ForumThread::with('category')->findOrFail($id);
        $this->assert($this->authority->canReply($request->user(), $thread));

        $data = $request->validate([
            'body' => ['required', 'string', 'min:2', 'max:20000'],
        ]);

        DB::transaction(fn () => $this->appendPost($thread, $request->user()->id, $data['body']));

        return redirect()->route('forum.thread', [$thread->id, 'page' => 'last'])
            ->with('success', 'Reply posted.');
    }

    /** Edits a post, recording that it was edited and by whom. */
    public function updatePost(Request $request, int $id): RedirectResponse
    {
        $post = ForumPost::with('thread.category')->findOrFail($id);
        $this->assert($this->authority->canEditPost($request->user(), $post));

        $data = $request->validate([
            'body' => ['required', 'string', 'min:2', 'max:20000'],
        ]);

        // Who edited and when are not mass-assignable, so that no request body
        // can ever claim somebody else made the change.
        $post->forceFill([
            'body' => $data['body'],
            'edited_at' => now(),
            'edited_by_id' => $request->user()->id,
        ])->save();

        return redirect()->route('forum.thread', $post->thread_id)
            ->with('success', 'Post updated.');
    }

    /** Raises a report for the forum moderators. */
    public function report(Request $request, int $id): RedirectResponse
    {
        $post = ForumPost::with('thread.category')->findOrFail($id);
        $this->assert($this->authority->canReadThread($request->user(), $post->thread));

        $data = $request->validate([
            'reason' => ['required', 'string', 'min:4', 'max:512'],
        ]);

        // Reporting the same post twice is the same report, not a louder one.
        ForumReport::updateOrCreate(
            ['post_id' => $post->id, 'reporter_id' => $request->user()->id],
            ['reason' => $data['reason']]
        );

        return back()->with('success', 'Reported. A moderator will take a look.');
    }

    // ─── moderating ─────────────────────────────────────────────────────────

    /** Pins, locks or hides a thread. */
    public function moderateThread(Request $request, int $id): RedirectResponse
    {
        $thread = ForumThread::with('category')->findOrFail($id);
        $this->assert($this->authority->canModerateThread($request->user(), $thread));

        $data = $request->validate([
            'action' => ['required', 'in:pin,unpin,lock,unlock,hide,unhide'],
        ]);

        $thread->forceFill(match ($data['action']) {
            'pin' => ['pinned' => true],
            'unpin' => ['pinned' => false],
            'lock' => ['locked' => true],
            'unlock' => ['locked' => false],
            'hide' => ['hidden' => true],
            'unhide' => ['hidden' => false],
        })->save();

        return back()->with('success', 'Thread updated.');
    }

    /** Hides or restores a post, recording who did it and why. */
    public function moderatePost(Request $request, int $id): RedirectResponse
    {
        $post = ForumPost::with('thread.category')->findOrFail($id);
        $this->assert($this->authority->canModerateThread($request->user(), $post->thread));

        $data = $request->validate([
            'action' => ['required', 'in:hide,restore'],
            'reason' => ['required_if:action,hide', 'nullable', 'string', 'max:255'],
        ]);

        $post->forceFill($data['action'] === 'hide'
            ? [
                'hidden' => true,
                'hidden_at' => now(),
                'hidden_by_id' => $request->user()->id,
                'hidden_reason' => $data['reason'],
            ]
            : [
                'hidden' => false,
                'hidden_at' => null,
                'hidden_by_id' => null,
                'hidden_reason' => null,
            ])->save();

        return back()->with('success', 'Post updated.');
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    /**
     * Writes a post and brings the thread and its board up to date.
     *
     * The counters and "last post" markers are kept on the rows rather than
     * counted on every page load, because the board list would otherwise scan
     * every post in the forum to draw one column.
     */
    private function appendPost(ForumThread $thread, int $authorId, string $body): ForumPost
    {
        $post = ForumPost::create([
            'thread_id' => $thread->id,
            'author_id' => $authorId,
            'body' => $body,
        ]);

        $isFirstPost = $thread->posts()->count() === 1;

        $thread->forceFill([
            'last_reply_at' => $post->created_at,
            'last_post_id' => $post->id,
            'last_poster_id' => $authorId,
            // The opening post is not a reply to itself.
            'reply_count' => $isFirstPost ? 0 : $thread->reply_count + 1,
        ])->save();

        if ($thread->category_id !== null) {
            DB::table('habnut_forum_categories')
                ->where('id', $thread->category_id)
                ->update([
                    'post_count' => DB::raw('post_count + 1'),
                    'last_thread_id' => $thread->id,
                    'last_post_at' => $post->created_at,
                ]);
        }

        return $post;
    }

    /** Recent threads across every board this user can see. */
    private function latestThreads($user)
    {
        return ForumThread::listed()
            ->whereNotNull('category_id')
            ->whereHas('category', fn ($q) => $q->where('min_read_rank', '<=', $user?->rank ?? 0))
            ->with(['category:id,slug,name', 'lastPoster:id,username'])
            ->reorder()
            ->orderByDesc('last_reply_at')
            ->limit(8)
            ->get();
    }

    /** Refuses a request the viewer is not allowed to make. */
    private function assert(bool $allowed): void
    {
        if (! $allowed) {
            throw new AccessDeniedHttpException('You cannot do that here.');
        }
    }
}
