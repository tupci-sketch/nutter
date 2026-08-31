<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

/**
 * The review queue behind the automatic mutes.
 *
 * The automatic half of this lives in the emulator and is tested there. What
 * matters here is the promise the automatic half makes: that a person looks, and
 * that when they decide in the player's favour the player actually gets their
 * voice back.
 */
class DccAutoModerationTest extends TestCase
{
    use RefreshDatabase;

    private function admin(): User
    {
        return User::factory()->create(['rank' => User::RANK_ADMIN]);
    }

    /** An automatic mute with its case, as the emulator would have written it. */
    private function openCase(string $category = 'threat', string $message = 'i will hurt you',
                              ?string $username = null): object
    {
        $player = $username === null
            ? User::factory()->create()
            : User::factory()->create(['username' => $username]);

        $muteId = DB::table('habnut_mutes')->insertGetId([
            'user_id' => $player->id,
            'muted_by_id' => null,
            'reason' => 'Automatic: threat (awaiting review)',
            'expires_at' => now()->addDay(),
            'source' => 'automatic',
            'created_at' => now(),
        ]);

        $caseId = DB::table('habnut_auto_mutes')->insertGetId([
            'user_id' => $player->id,
            'mute_id' => $muteId,
            'category' => $category,
            'message' => $message,
            'created_at' => now(),
        ]);

        return (object) ['player' => $player, 'muteId' => $muteId, 'caseId' => $caseId];
    }

    public function test_the_queue_needs_staff(): void
    {
        $this->actingAs(User::factory()->create())->get('/dcc/automod')->assertForbidden();
        $this->actingAs($this->admin())->get('/dcc/automod')->assertOk();
    }

    public function test_the_queue_shows_what_was_actually_said(): void
    {
        $this->openCase('threat', 'i will hurt you', 'tupci');

        // A reviewer judges the message, not the rule's opinion of it.
        $this->actingAs($this->admin())->get('/dcc/automod')
            ->assertOk()
            ->assertSee('i will hurt you')
            ->assertSee('tupci');
    }

    public function test_lifting_a_mute_gives_the_player_their_voice_back(): void
    {
        $case = $this->openCase();

        $this->actingAs($this->admin())
            ->post("/dcc/automod/{$case->caseId}/review", [
                'decision' => 'overturn',
                'notes' => 'Quoting a film',
            ])
            ->assertRedirect();

        $this->assertSame('overturned',
            DB::table('habnut_auto_mutes')->where('id', $case->caseId)->value('status'));
        $this->assertNotNull(
            DB::table('habnut_mutes')->where('id', $case->muteId)->value('lifted_at'),
            'a decision in the player\'s favour must actually free them'
        );
    }

    public function test_upholding_a_mute_leaves_it_running(): void
    {
        $case = $this->openCase();

        $this->actingAs($this->admin())
            ->post("/dcc/automod/{$case->caseId}/review", ['decision' => 'uphold'])
            ->assertRedirect();

        $this->assertSame('upheld',
            DB::table('habnut_auto_mutes')->where('id', $case->caseId)->value('status'));
        $this->assertNull(
            DB::table('habnut_mutes')->where('id', $case->muteId)->value('lifted_at'));
    }

    public function test_a_settled_case_is_not_decided_twice(): void
    {
        $case = $this->openCase();
        $admin = $this->admin();

        $this->actingAs($admin)->post("/dcc/automod/{$case->caseId}/review", ['decision' => 'uphold']);
        $this->actingAs($admin)
            ->post("/dcc/automod/{$case->caseId}/review", ['decision' => 'overturn'])
            ->assertSessionHas('error');

        $this->assertNull(
            DB::table('habnut_mutes')->where('id', $case->muteId)->value('lifted_at'),
            'a settled case should not quietly reopen'
        );
    }

    public function test_reviewing_answers_any_outstanding_help_request(): void
    {
        $case = $this->openCase();
        DB::table('habnut_mute_help_requests')->insert([
            'auto_mute_id' => $case->caseId,
            'user_id' => $case->player->id,
            'message' => 'I was quoting a film',
            'created_at' => now(),
        ]);

        $this->actingAs($this->admin())
            ->post("/dcc/automod/{$case->caseId}/review", [
                'decision' => 'overturn',
                'notes' => 'Fair enough',
            ]);

        $help = DB::table('habnut_mute_help_requests')->first();
        $this->assertNotNull($help->handled_at, 'somebody who asked has now been answered');
        $this->assertSame('Fair enough', $help->response);
    }

    public function test_the_queue_shows_what_a_muted_player_said_about_it(): void
    {
        $case = $this->openCase();
        DB::table('habnut_mute_help_requests')->insert([
            'auto_mute_id' => $case->caseId,
            'user_id' => $case->player->id,
            'message' => 'I was quoting a film',
            'created_at' => now(),
        ]);

        $this->actingAs($this->admin())->get('/dcc/automod')
            ->assertOk()
            ->assertSee('I was quoting a film');
    }

    public function test_the_worst_categories_come_first(): void
    {
        $this->openCase('doxxing', 'someones number');
        $this->openCase('minor_safety', 'the urgent one');

        $response = $this->actingAs($this->admin())->get('/dcc/automod')->assertOk();

        $body = $response->getContent();
        $this->assertLessThan(
            strpos($body, 'someones number'),
            strpos($body, 'the urgent one'),
            'the queue should be worked in the order that matters, not the order it arrived'
        );
    }

    public function test_a_review_is_recorded_in_the_audit_log(): void
    {
        $case = $this->openCase();

        $this->actingAs($this->admin())
            ->post("/dcc/automod/{$case->caseId}/review", ['decision' => 'uphold']);

        $this->assertTrue(
            DB::table('habnut_audit_logs')->where('action', 'automute_upheld')->exists());
    }

    // ─── rules ──────────────────────────────────────────────────────────────

    public function test_a_rule_can_be_added_and_switched_off(): void
    {
        $admin = $this->admin();

        $this->actingAs($admin)->post('/dcc/automod/rules', [
            'category' => 'scam',
            'label' => 'Asking for a recovery code',
            'pattern' => '(?<![a-z])(recovery|backup)[ ]+code(?![a-z])',
            'match_mode' => 'words',
            'action' => 'mute',
            'severity' => 4,
            'mute_minutes' => 1440,
        ])->assertRedirect();

        $rule = DB::table('habnut_content_rules')->where('category', 'scam')->first();
        $this->assertNotNull($rule);
        $this->assertTrue((bool) $rule->enabled);

        $this->actingAs($admin)->post("/dcc/automod/rules/{$rule->id}/toggle")->assertRedirect();
        $this->assertFalse(
            (bool) DB::table('habnut_content_rules')->where('id', $rule->id)->value('enabled'));
    }

    public function test_a_pattern_with_a_backslash_is_refused(): void
    {
        // A backslash means different things to different databases, so a rule
        // written with one works in one place and silently matches nothing in
        // another.
        $this->actingAs($this->admin())->post('/dcc/automod/rules', [
            'category' => 'scam',
            'label' => 'Broken',
            'pattern' => '\\bpassword\\b',
            'match_mode' => 'words',
            'action' => 'mute',
            'severity' => 3,
            'mute_minutes' => 60,
        ])->assertStatus(422);

        $this->assertSame(0, DB::table('habnut_content_rules')->count());
    }

    public function test_a_pattern_that_will_not_compile_is_refused(): void
    {
        // A rule that will not compile is skipped at load time, so the hotel
        // would run with one fewer protection than whoever wrote it believes.
        $this->actingAs($this->admin())->post('/dcc/automod/rules', [
            'category' => 'threat',
            'label' => 'Broken',
            'pattern' => '([unclosed',
            'match_mode' => 'words',
            'action' => 'mute',
            'severity' => 3,
            'mute_minutes' => 60,
        ])->assertStatus(422);

        $this->assertSame(0, DB::table('habnut_content_rules')->count());
    }

    public function test_the_rules_page_lists_what_is_in_force(): void
    {
        DB::table('habnut_content_rules')->insert([
            'category' => 'self_harm',
            'label' => 'Telling somebody to end their life',
            'pattern' => '(?<![a-z])kys(?![a-z])',
            'match_mode' => 'both',
            'action' => 'mute',
            'severity' => 5,
            'mute_minutes' => 4320,
            'enabled' => true,
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        $this->actingAs($this->admin())->get('/dcc/automod/rules')
            ->assertOk()
            ->assertSee('Telling somebody to end their life');
    }
}
