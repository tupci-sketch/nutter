<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

/**
 * The pages that show the hotel to itself.
 *
 * Both are public on purpose. A hotel that will not say who its staff are
 * invites players to believe whoever claims to be one, and the staff page is
 * where somebody checks before handing over anything.
 */
class CommunityPagesTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        cache()->flush();
    }

    public function test_the_team_page_lists_staff_by_rank(): void
    {
        User::factory()->create(['username' => 'tupci', 'rank' => User::RANK_ADMIN]);
        User::factory()->create(['username' => 'helper', 'rank' => User::RANK_HELPER]);
        User::factory()->create(['username' => 'ordinary', 'rank' => User::RANK_MEMBER]);

        $response = $this->get('/staff');

        $response->assertOk()
            ->assertSee('tupci')
            ->assertSee('helper')
            ->assertSee('Administrator')
            ->assertDontSee('ordinary');
    }

    public function test_the_team_page_warns_about_impersonation(): void
    {
        // The most useful thing this page does is let somebody check.
        $this->get('/staff')->assertOk()->assertSee('password', false);
    }

    public function test_the_team_page_is_public(): void
    {
        User::factory()->create(['username' => 'tupci', 'rank' => User::RANK_MODERATOR]);

        $this->get('/staff')->assertOk();
    }

    public function test_the_stats_page_counts_the_hotel(): void
    {
        User::factory()->count(3)->create();
        User::factory()->create(['online' => true]);
        DB::table('habnut_rooms')->insert([
            'name' => 'The White House',
            'owner_id' => User::first()->id,
            'user_count' => 12,
            'max_users' => 25,
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        $this->get('/stats')
            ->assertOk()
            ->assertSee('Players')
            ->assertSee('The White House');
    }

    public function test_the_stats_page_ranks_players_by_achievement_score(): void
    {
        User::factory()->create(['username' => 'quiet', 'achievement_score' => 5]);
        User::factory()->create(['username' => 'tupci', 'achievement_score' => 4200]);

        $body = $this->get('/stats')->assertOk()->getContent();

        $this->assertLessThan(strpos($body, 'quiet'), strpos($body, 'tupci'));
    }

    public function test_the_stats_page_shows_game_leaderboards(): void
    {
        $player = User::factory()->create(['username' => 'tupci']);
        DB::table('habnut_leaderboards')->insert([
            'user_id' => $player->id,
            'game_type' => 'snowstorm',
            'period' => 'all_time',
            'matches_played' => 20,
            'wins' => 14,
            'total_score' => 980,
            'updated_at' => now(),
        ]);

        $this->get('/stats')->assertOk()->assertSee('snowstorm')->assertSee('14');
    }

    public function test_the_stats_page_holds_up_with_an_empty_hotel(): void
    {
        // A brand new hotel should not show an error page on the day it opens.
        $this->get('/stats')->assertOk()->assertSee('Players');
    }
}
