<?php

namespace Tests\Feature;

use App\Models\ForumCategory;
use App\Models\ForumModerator;
use App\Models\ForumPost;
use App\Models\ForumThread;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

/**
 * The forums are the loudest public part of the site, so who may read, write
 * and moderate is worth pinning down. These exercise the routes for real: a
 * permission that only holds in the view and not in the request would let a
 * hand-made POST through, so every check is made against the POST.
 */
class ForumTest extends TestCase
{
    use RefreshDatabase;

    private function board(array $overrides = []): ForumCategory
    {
        return ForumCategory::create(array_merge([
            'slug' => 'general',
            'name' => 'General',
            'description' => 'Anything and everything.',
            'min_read_rank' => 0,
            'min_post_rank' => 1,
            'sort_order' => 10,
        ], $overrides));
    }

    private function threadOn(ForumCategory $board, User $author, array $overrides = []): ForumThread
    {
        $thread = ForumThread::create([
            'category_id' => $board->id,
            'author_id' => $author->id,
            'title' => 'A thread about something',
        ]);

        // Pinned, locked and hidden are not mass-assignable, so a test sets them
        // the same way the moderation route does.
        if ($overrides !== []) {
            $thread->forceFill($overrides)->save();
        }

        ForumPost::create([
            'thread_id' => $thread->id,
            'author_id' => $author->id,
            'body' => 'The opening post.',
        ]);

        return $thread->fresh();
    }

    // ─── reading ────────────────────────────────────────────────────────────

    public function test_guests_can_read_public_boards(): void
    {
        $this->board();

        $this->get('/forum')->assertOk()->assertSee('General');
        $this->get('/forum/board/general')->assertOk();
    }

    public function test_a_staff_board_is_invisible_to_players(): void
    {
        $this->board(['slug' => 'staff-room', 'name' => 'Staff Room', 'min_read_rank' => 3]);

        $this->get('/forum')->assertOk()->assertDontSee('Staff Room');
        $this->get('/forum/board/staff-room')->assertForbidden();

        $staff = User::factory()->create(['rank' => User::RANK_MODERATOR]);
        $this->actingAs($staff)->get('/forum/board/staff-room')->assertOk();
    }

    public function test_a_thread_shows_its_posts(): void
    {
        $board = $this->board();
        $author = User::factory()->create(['username' => 'tupci']);
        $thread = $this->threadOn($board, $author);

        $this->get("/forum/thread/{$thread->id}")
            ->assertOk()
            ->assertSee('A thread about something')
            ->assertSee('The opening post.')
            ->assertSee('tupci');
    }

    public function test_reading_a_thread_counts_a_view(): void
    {
        $board = $this->board();
        $thread = $this->threadOn($board, User::factory()->create());

        $this->get("/forum/thread/{$thread->id}")->assertOk();

        $this->assertSame(1, $thread->fresh()->views);
    }

    // ─── writing ────────────────────────────────────────────────────────────

    public function test_a_member_can_start_a_thread(): void
    {
        $board = $this->board();
        $tupci = User::factory()->create(['username' => 'tupci']);

        $this->actingAs($tupci)
            ->post('/forum/board/general/thread', [
                'title' => 'Has anyone seen the new furni',
                'body' => 'It went up in the catalogue this morning.',
            ])
            ->assertRedirect();

        $thread = ForumThread::first();
        $this->assertSame('Has anyone seen the new furni', $thread->title);
        $this->assertSame($tupci->id, (int) $thread->author_id);
        // The opening post is not a reply to itself.
        $this->assertSame(0, (int) $thread->reply_count);
        $this->assertSame(1, (int) $board->fresh()->thread_count);
    }

    public function test_a_guest_cannot_start_a_thread(): void
    {
        $this->board();

        $this->post('/forum/board/general/thread', [
            'title' => 'Sneaking in',
            'body' => 'Without an account.',
        ])->assertRedirect('/login');

        $this->assertSame(0, ForumThread::count());
    }

    public function test_a_reply_moves_the_thread_up_the_board(): void
    {
        $board = $this->board();
        $thread = $this->threadOn($board, User::factory()->create());
        $replier = User::factory()->create();

        $this->travel(2)->minutes();
        $this->actingAs($replier)
            ->post("/forum/thread/{$thread->id}/reply", ['body' => 'Yes, it looks good.'])
            ->assertRedirect();

        $thread = $thread->fresh();
        $this->assertSame(1, (int) $thread->reply_count);
        $this->assertSame($replier->id, (int) $thread->last_poster_id);
        $this->assertTrue($thread->last_reply_at->greaterThan($thread->created_at));
    }

    public function test_a_locked_thread_takes_no_replies(): void
    {
        $board = $this->board();
        $thread = $this->threadOn($board, User::factory()->create(), ['locked' => true]);

        $this->actingAs(User::factory()->create())
            ->post("/forum/thread/{$thread->id}/reply", ['body' => 'Getting the last word.'])
            ->assertForbidden();

        $this->assertSame(1, ForumPost::count());
    }

    public function test_a_locked_board_takes_no_new_threads(): void
    {
        $this->board(['locked' => true]);

        $this->actingAs(User::factory()->create())
            ->post('/forum/board/general/thread', ['title' => 'Reviving this', 'body' => 'Hello?'])
            ->assertForbidden();
    }

    public function test_only_staff_may_post_to_an_announcements_board(): void
    {
        $this->board(['slug' => 'announcements', 'name' => 'Announcements', 'min_post_rank' => 4]);

        $this->actingAs(User::factory()->create())
            ->post('/forum/board/announcements/thread', ['title' => 'Notice', 'body' => 'Listen up.'])
            ->assertForbidden();

        $admin = User::factory()->create(['rank' => User::RANK_ADMIN]);
        $this->actingAs($admin)
            ->post('/forum/board/announcements/thread', ['title' => 'Notice', 'body' => 'Listen up.'])
            ->assertRedirect();
    }

    public function test_a_muted_player_cannot_post(): void
    {
        $board = $this->board();
        $shouty = User::factory()->create();

        DB::table('habnut_mutes')->insert([
            'user_id' => $shouty->id,
            'muted_by_id' => $shouty->id,
            'reason' => 'Under review',
            'expires_at' => now()->addHour(),
            'created_at' => now(),
        ]);

        $this->actingAs($shouty)
            ->post('/forum/board/general/thread', ['title' => 'Still here', 'body' => 'Hello again.'])
            ->assertForbidden();
    }

    public function test_a_lifted_mute_lets_a_player_post_again(): void
    {
        $this->board();
        $player = User::factory()->create();

        DB::table('habnut_mutes')->insert([
            'user_id' => $player->id,
            'muted_by_id' => $player->id,
            'reason' => 'Reviewed and cleared',
            'expires_at' => now()->addHour(),
            'lifted_at' => now(),
            'created_at' => now(),
        ]);

        $this->actingAs($player)
            ->post('/forum/board/general/thread', ['title' => 'Back', 'body' => 'Thanks for sorting that.'])
            ->assertRedirect();
    }

    // ─── editing ────────────────────────────────────────────────────────────

    public function test_an_author_may_correct_a_recent_post(): void
    {
        $board = $this->board();
        $author = User::factory()->create();
        $thread = $this->threadOn($board, $author);
        $post = ForumPost::first();

        $this->actingAs($author)
            ->put("/forum/post/{$post->id}", ['body' => 'The opening post, tidied up.'])
            ->assertRedirect();

        $post = $post->fresh();
        $this->assertSame('The opening post, tidied up.', $post->body);
        $this->assertNotNull($post->edited_at, 'an edit should be visible to readers');
    }

    public function test_an_old_post_settles_and_stops_being_editable(): void
    {
        $board = $this->board();
        $author = User::factory()->create();
        $this->threadOn($board, $author);
        $post = ForumPost::first();

        $this->travel(2)->hours();

        $this->actingAs($author)
            ->put("/forum/post/{$post->id}", ['body' => 'Rewriting history.'])
            ->assertForbidden();
    }

    public function test_a_player_cannot_edit_somebody_elses_post(): void
    {
        $board = $this->board();
        $this->threadOn($board, User::factory()->create());
        $post = ForumPost::first();

        $this->actingAs(User::factory()->create())
            ->put("/forum/post/{$post->id}", ['body' => 'Putting words in their mouth.'])
            ->assertForbidden();
    }

    public function test_a_forum_administrator_may_edit_any_post(): void
    {
        $board = $this->board();
        $this->threadOn($board, User::factory()->create());
        $post = ForumPost::first();

        $boss = User::factory()->create();
        ForumModerator::create([
            'user_id' => $boss->id,
            'scope' => ForumModerator::SCOPE_GLOBAL,
            'scope_id' => null,
            'role' => ForumModerator::ROLE_ADMINISTRATOR,
            'granted_by_id' => $boss->id,
        ]);

        $this->actingAs($boss)
            ->put("/forum/post/{$post->id}", ['body' => 'Removed a phone number.'])
            ->assertRedirect();

        $this->assertSame($boss->id, (int) $post->fresh()->edited_by_id);
    }

    // ─── moderating ─────────────────────────────────────────────────────────

    public function test_a_board_moderator_can_pin_and_lock_only_their_board(): void
    {
        $general = $this->board();
        $trading = $this->board(['slug' => 'trading', 'name' => 'Trading']);

        $mod = User::factory()->create();
        ForumModerator::create([
            'user_id' => $mod->id,
            'scope' => ForumModerator::SCOPE_CATEGORY,
            'scope_id' => $general->id,
            'role' => ForumModerator::ROLE_MODERATOR,
            'granted_by_id' => $mod->id,
        ]);

        $mine = $this->threadOn($general, User::factory()->create());
        $theirs = $this->threadOn($trading, User::factory()->create());

        $this->actingAs($mod)
            ->post("/forum/thread/{$mine->id}/moderate", ['action' => 'pin'])
            ->assertRedirect();
        $this->assertTrue($mine->fresh()->pinned);

        $this->actingAs($mod)
            ->post("/forum/thread/{$theirs->id}/moderate", ['action' => 'lock'])
            ->assertForbidden();
        $this->assertFalse($theirs->fresh()->locked);
    }

    public function test_hiding_a_post_records_who_did_it_and_why(): void
    {
        $board = $this->board();
        $this->threadOn($board, User::factory()->create());
        $post = ForumPost::first();
        $mod = User::factory()->create(['rank' => User::RANK_MODERATOR]);

        $this->actingAs($mod)
            ->post("/forum/post/{$post->id}/moderate", [
                'action' => 'hide',
                'reason' => 'Personal information',
            ])
            ->assertRedirect();

        $post = $post->fresh();
        $this->assertTrue($post->hidden);
        $this->assertSame($mod->id, (int) $post->hidden_by_id);
        $this->assertSame('Personal information', $post->hidden_reason);
    }

    public function test_a_hidden_post_is_kept_from_readers_but_shown_to_moderators(): void
    {
        $board = $this->board();
        $thread = $this->threadOn($board, User::factory()->create());
        ForumPost::first()->forceFill([
            'hidden' => true,
            'hidden_reason' => 'Personal information',
        ])->save();

        $this->get("/forum/thread/{$thread->id}")
            ->assertOk()
            ->assertDontSee('The opening post.');

        $mod = User::factory()->create(['rank' => User::RANK_MODERATOR]);
        $this->actingAs($mod)
            ->get("/forum/thread/{$thread->id}")
            ->assertOk()
            ->assertSee('The opening post.')
            ->assertSee('Personal information');
    }

    public function test_a_hidden_thread_is_kept_from_readers(): void
    {
        $board = $this->board();
        $thread = $this->threadOn($board, User::factory()->create(), ['hidden' => true]);

        $this->get("/forum/thread/{$thread->id}")->assertForbidden();

        $mod = User::factory()->create(['rank' => User::RANK_MODERATOR]);
        $this->actingAs($mod)->get("/forum/thread/{$thread->id}")->assertOk();
    }

    // ─── reporting ──────────────────────────────────────────────────────────

    public function test_reporting_the_same_post_twice_raises_one_report(): void
    {
        $board = $this->board();
        $this->threadOn($board, User::factory()->create());
        $post = ForumPost::first();
        $reporter = User::factory()->create();

        $this->actingAs($reporter)
            ->post("/forum/post/{$post->id}/report", ['reason' => 'Someone posted an address'])
            ->assertRedirect();
        $this->actingAs($reporter)
            ->post("/forum/post/{$post->id}/report", ['reason' => 'Still there'])
            ->assertRedirect();

        $this->assertSame(1, DB::table('habnut_forum_reports')->count());
        $this->assertSame('Still there',
            DB::table('habnut_forum_reports')->value('reason'));
    }

    // ─── group forums ───────────────────────────────────────────────────────

    private function group(string $forumMode, User $owner): int
    {
        return DB::table('habnut_groups')->insertGetId([
            'name' => 'The Acorn Club',
            'description' => 'A group for people who like acorns.',
            'owner_id' => $owner->id,
            'badge' => 'b03120s13181',
            'forum_mode' => $forumMode,
            'member_count' => 1,
            'created_at' => now(),
            'updated_at' => now(),
        ]);
    }

    private function join(int $groupId, User $user, string $rank): void
    {
        DB::table('habnut_group_members')->insert([
            'group_id' => $groupId,
            'user_id' => $user->id,
            'rank' => $rank,
            'created_at' => now(),
            'updated_at' => now(),
        ]);
    }

    public function test_a_members_only_group_forum_is_closed_to_outsiders(): void
    {
        $owner = User::factory()->create();
        $groupId = $this->group('members_only', $owner);
        $this->join($groupId, $owner, 'owner');

        $this->actingAs(User::factory()->create())
            ->get("/forum/group/{$groupId}")
            ->assertForbidden();

        $this->actingAs($owner)->get("/forum/group/{$groupId}")->assertOk();
    }

    public function test_an_open_group_forum_can_be_read_by_anyone_but_posted_by_members(): void
    {
        $owner = User::factory()->create();
        $groupId = $this->group('open', $owner);
        $this->join($groupId, $owner, 'owner');
        $outsider = User::factory()->create();

        $this->actingAs($outsider)->get("/forum/group/{$groupId}")->assertOk();

        $this->actingAs($outsider)
            ->post("/forum/group/{$groupId}/thread", ['title' => 'Hello', 'body' => 'Can I join?'])
            ->assertForbidden();

        $this->actingAs($owner)
            ->post("/forum/group/{$groupId}/thread", ['title' => 'Welcome', 'body' => 'Say hello here.'])
            ->assertRedirect();
    }

    public function test_an_admins_only_group_forum_keeps_ordinary_members_reading(): void
    {
        $owner = User::factory()->create();
        $member = User::factory()->create();
        $groupId = $this->group('admins_only', $owner);
        $this->join($groupId, $owner, 'owner');
        $this->join($groupId, $member, 'member');

        $this->actingAs($member)->get("/forum/group/{$groupId}")->assertOk();
        $this->actingAs($member)
            ->post("/forum/group/{$groupId}/thread", ['title' => 'A word', 'body' => 'From a member.'])
            ->assertForbidden();
    }

    public function test_a_disabled_group_forum_is_closed_to_its_own_members(): void
    {
        $owner = User::factory()->create();
        $groupId = $this->group('disabled', $owner);
        $this->join($groupId, $owner, 'owner');

        $this->actingAs($owner)->get("/forum/group/{$groupId}")->assertForbidden();
    }

    public function test_a_group_owner_moderates_their_own_forum(): void
    {
        $owner = User::factory()->create();
        $member = User::factory()->create();
        $groupId = $this->group('open', $owner);
        $this->join($groupId, $owner, 'owner');
        $this->join($groupId, $member, 'member');

        $thread = ForumThread::create([
            'group_id' => $groupId,
            'author_id' => $member->id,
            'title' => 'A thread in the group',
        ]);

        $this->actingAs($owner)
            ->post("/forum/thread/{$thread->id}/moderate", ['action' => 'lock'])
            ->assertRedirect();
        $this->assertTrue($thread->fresh()->locked);

        $this->actingAs($member)
            ->post("/forum/thread/{$thread->id}/moderate", ['action' => 'unlock'])
            ->assertForbidden();
    }
}
