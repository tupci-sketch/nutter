<?php

namespace Tests\Feature;

use App\Models\ForumCategory;
use App\Models\ForumModerator;
use App\Models\ForumPost;
use App\Models\ForumReport;
use App\Models\ForumThread;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

/** Staff administration of the forums, and the report queue behind it. */
class DccForumTest extends TestCase
{
    use RefreshDatabase;

    private function admin(): User
    {
        return User::factory()->create(['rank' => User::RANK_ADMIN]);
    }

    private function reportedPost(): ForumReport
    {
        $board = ForumCategory::create([
            'slug' => 'general', 'name' => 'General',
            'min_read_rank' => 0, 'min_post_rank' => 1, 'sort_order' => 10,
        ]);
        $thread = ForumThread::create([
            'category_id' => $board->id,
            'author_id' => User::factory()->create()->id,
            'title' => 'A thread',
        ]);
        $post = ForumPost::create([
            'thread_id' => $thread->id,
            'author_id' => $thread->author_id,
            'body' => 'Something worth reporting.',
        ]);

        return ForumReport::create([
            'post_id' => $post->id,
            'reporter_id' => User::factory()->create()->id,
            'reason' => 'Personal information',
        ]);
    }

    public function test_the_forum_page_needs_staff(): void
    {
        $this->actingAs(User::factory()->create())->get('/dcc/forum')->assertForbidden();
        $this->actingAs($this->admin())->get('/dcc/forum')->assertOk();
    }

    public function test_a_board_can_be_created_and_reshaped(): void
    {
        $admin = $this->admin();

        $this->actingAs($admin)->post('/dcc/forum/category', [
            'name' => 'Fan Art',
            'slug' => 'fan-art',
            'description' => 'Show us what you made.',
            'min_read_rank' => 0,
            'min_post_rank' => 1,
            'sort_order' => 80,
        ])->assertRedirect();

        $board = ForumCategory::where('slug', 'fan-art')->firstOrFail();
        $this->assertSame('Fan Art', $board->name);

        $this->actingAs($admin)->put("/dcc/forum/category/{$board->id}", [
            'name' => 'Fan Art and Pixels',
            'description' => 'Show us what you made.',
            'min_read_rank' => 0,
            'min_post_rank' => 2,
            'sort_order' => 80,
            'locked' => 1,
        ])->assertRedirect();

        $board = $board->fresh();
        $this->assertSame('Fan Art and Pixels', $board->name);
        $this->assertTrue($board->locked);
        $this->assertSame(2, (int) $board->min_post_rank);
    }

    public function test_a_slug_cannot_be_taken_twice(): void
    {
        ForumCategory::create([
            'slug' => 'general', 'name' => 'General',
            'min_read_rank' => 0, 'min_post_rank' => 1, 'sort_order' => 10,
        ]);

        $this->actingAs($this->admin())->post('/dcc/forum/category', [
            'name' => 'General Again',
            'slug' => 'general',
            'min_read_rank' => 0,
            'min_post_rank' => 1,
            'sort_order' => 20,
        ])->assertSessionHasErrors('slug');
    }

    public function test_a_forum_role_can_be_granted_and_taken_back(): void
    {
        $admin = $this->admin();
        $board = ForumCategory::create([
            'slug' => 'trading', 'name' => 'Trading',
            'min_read_rank' => 0, 'min_post_rank' => 1, 'sort_order' => 10,
        ]);
        $helper = User::factory()->create(['username' => 'tupci']);

        $this->actingAs($admin)->post('/dcc/forum/role', [
            'username' => 'tupci',
            'scope' => ForumModerator::SCOPE_CATEGORY,
            'scope_id' => $board->id,
            'role' => ForumModerator::ROLE_MODERATOR,
        ])->assertRedirect();

        $role = ForumModerator::where('user_id', $helper->id)->firstOrFail();
        $this->assertSame((string) $board->id, (string) $role->scope_id);

        $this->actingAs($admin)->delete("/dcc/forum/role/{$role->id}")->assertRedirect();
        $this->assertSame(0, ForumModerator::count());
    }

    public function test_a_scoped_role_must_name_its_scope(): void
    {
        User::factory()->create(['username' => 'tupci']);

        $this->actingAs($this->admin())->post('/dcc/forum/role', [
            'username' => 'tupci',
            'scope' => ForumModerator::SCOPE_CATEGORY,
            'role' => ForumModerator::ROLE_MODERATOR,
        ])->assertSessionHasErrors('scope_id');

        $this->assertSame(0, ForumModerator::count());
    }

    public function test_a_global_role_needs_no_scope(): void
    {
        User::factory()->create(['username' => 'tupci']);

        $this->actingAs($this->admin())->post('/dcc/forum/role', [
            'username' => 'tupci',
            'scope' => ForumModerator::SCOPE_GLOBAL,
            'role' => ForumModerator::ROLE_ADMINISTRATOR,
        ])->assertRedirect();

        $this->assertNull(ForumModerator::firstOrFail()->scope_id);
    }

    public function test_upholding_a_report_also_hides_the_post(): void
    {
        $report = $this->reportedPost();

        $this->actingAs($this->admin())
            ->post("/dcc/forum/reports/{$report->id}", [
                'decision' => 'uphold',
                'notes' => 'Address removed',
            ])
            ->assertRedirect();

        $this->assertSame('upheld', $report->fresh()->status);
        // A report agreed with that leaves the post up has not been acted on.
        $this->assertTrue($report->post->fresh()->hidden);
        $this->assertSame('Address removed', $report->post->fresh()->hidden_reason);
    }

    public function test_dismissing_a_report_leaves_the_post_alone(): void
    {
        $report = $this->reportedPost();

        $this->actingAs($this->admin())
            ->post("/dcc/forum/reports/{$report->id}", ['decision' => 'dismiss'])
            ->assertRedirect();

        $this->assertSame('dismissed', $report->fresh()->status);
        $this->assertFalse($report->post->fresh()->hidden);
    }

    public function test_a_hidden_post_can_be_restored(): void
    {
        $report = $this->reportedPost();
        $admin = $this->admin();

        $this->actingAs($admin)->post("/dcc/forum/reports/{$report->id}", ['decision' => 'uphold']);
        $this->actingAs($admin)->post("/dcc/forum/post/{$report->post_id}/restore")->assertRedirect();

        $post = $report->post->fresh();
        $this->assertFalse($post->hidden);
        $this->assertNull($post->hidden_reason);
    }

    public function test_settling_a_report_is_recorded_in_the_audit_log(): void
    {
        $report = $this->reportedPost();

        $this->actingAs($this->admin())
            ->post("/dcc/forum/reports/{$report->id}", ['decision' => 'uphold']);

        $this->assertTrue(
            DB::table('habnut_audit_logs')->where('action', 'forum_report_uphold')->exists(),
            'a moderator decision should leave a trail'
        );
    }

    public function test_the_report_queue_filters_by_status(): void
    {
        $report = $this->reportedPost();
        $admin = $this->admin();

        $this->actingAs($admin)->get('/dcc/forum/reports?status=open')
            ->assertOk()->assertSee('Personal information');

        $this->actingAs($admin)->post("/dcc/forum/reports/{$report->id}", ['decision' => 'dismiss']);

        $this->actingAs($admin)->get('/dcc/forum/reports?status=open')
            ->assertOk()->assertDontSee('Personal information');
        $this->actingAs($admin)->get('/dcc/forum/reports?status=dismissed')
            ->assertOk()->assertSee('Personal information');
    }
}
