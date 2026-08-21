<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/**
 * The landing page is the first thing every player sees, signed in or out.
 * These render it for real, so a Blade error or a missing route reaches the
 * build rather than the player.
 */
class HomePageTest extends TestCase
{
    use RefreshDatabase;

    public function test_guest_sees_the_hotel_introduction(): void
    {
        $response = $this->get('/');

        $response->assertOk();
        $response->assertSee('Create an account', false);
        $response->assertSee('online right now', false);
        $response->assertDontSee('Enter Hotel', false);
    }

    public function test_signed_in_player_sees_their_own_page(): void
    {
        $tupci = User::factory()->create([
            'username' => 'tupci',
            'motto' => 'Building something',
            'credits' => 12500,
            'diamonds' => 42,
            'nut_points' => 380,
            'achievement_score' => 1275,
        ]);

        $response = $this->actingAs($tupci)->get('/');

        $response->assertOk();
        $response->assertSee('tupci');
        $response->assertSee('Building something');
        // Balances render formatted, which is what a player reads.
        $response->assertSee('12,500');
        $response->assertSee('1,275');
        $response->assertSee('Enter Hotel', false);
    }

    public function test_online_count_reflects_players_in_the_hotel(): void
    {
        User::factory()->count(3)->create(['online' => true]);
        User::factory()->count(2)->create(['online' => false]);

        $response = $this->get('/');

        $response->assertOk();
        $response->assertSee('3 online right now', false);
    }

    public function test_staff_online_are_listed_and_members_are_not(): void
    {
        User::factory()->create([
            'username' => 'modstaff',
            'rank' => User::RANK_MODERATOR,
            'online' => true,
        ]);
        User::factory()->create([
            'username' => 'plainmember',
            'rank' => User::RANK_MEMBER,
            'online' => true,
        ]);

        $response = $this->get('/');

        $response->assertOk();
        $response->assertSee('modstaff');
        $response->assertSee('Moderator');

        // A member may still appear elsewhere on the page — the leaderboard
        // lists everyone — so assert on the staff badge rather than the name.
        $this->assertSame(
            1,
            substr_count($response->getContent(), 'badge badge-staff'),
            'only the moderator should carry a staff badge'
        );
    }

    public function test_top_players_are_ordered_by_achievement_score(): void
    {
        User::factory()->create(['username' => 'runnerup', 'achievement_score' => 500]);
        User::factory()->create(['username' => 'champion', 'achievement_score' => 900]);

        $response = $this->get('/');

        $response->assertOk();
        $response->assertSeeInOrder(['champion', 'runnerup']);
    }

    public function test_rank_names_map_to_thresholds(): void
    {
        $this->assertSame('Member',
            (new User(['rank' => User::RANK_MEMBER]))->rankName());
        $this->assertSame('Moderator',
            (new User(['rank' => User::RANK_MODERATOR]))->rankName());
        $this->assertSame('Administrator',
            (new User(['rank' => User::RANK_ADMIN]))->rankName());
        $this->assertSame('Administrator',
            (new User(['rank' => 99]))->rankName(), 'ranks above admin stay admin');
    }

    public function test_avatar_url_is_null_without_a_configured_imager(): void
    {
        config(['habnut.imager_url' => null]);

        $this->assertNull(User::factory()->make(['look' => 'hd-180-1'])->avatarUrl());
    }

    public function test_avatar_url_uses_the_imager_when_configured(): void
    {
        config(['habnut.imager_url' => 'https://imager.example/render/']);

        $url = User::factory()->make(['look' => 'hd-180-1.ch-255-62'])->avatarUrl('l');

        $this->assertStringContainsString('figure=hd-180-1.ch-255-62', urldecode($url));
        $this->assertStringContainsString('size=l', $url);
    }
}
