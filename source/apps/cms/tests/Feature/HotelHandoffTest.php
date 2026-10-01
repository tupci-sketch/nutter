<?php

namespace Tests\Feature;

use App\Models\User;
use App\Services\SessionTicketService;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Redis;
use Tests\TestCase;

/**
 * Signing in once.
 *
 * The website and the hotel are two programs that have to agree on exactly one
 * thing for a player to get in: the Redis key holding their ticket, and what is
 * in it. The hotel reads `habnut:ticket:<ticket>` and expects `<userId>:<world>`.
 * The site used to write `ticket:<ticket>` through Laravel's cache, which adds
 * its own prefix and serialises the value — so the hotel found nothing, and
 * nobody could get in from the website at all.
 *
 * These assertions are deliberately literal. They are the contract.
 */
class HotelHandoffTest extends TestCase
{
    use RefreshDatabase;

    private function tupci(array $overrides = []): User
    {
        return User::factory()->create(array_merge([
            'username' => 'tupci',
            'email' => 'tupci@icloud.com',
            'password_hash' => Hash::make('testpassword123'),
            'rank' => 1,
            'two_fa_enabled' => false,
            'email_verified_at' => now(),
        ], $overrides));
    }

    /** @test */
    public function a_ticket_is_written_where_the_hotel_looks_for_it(): void
    {
        $user = $this->tupci();

        $written = [];
        Redis::shouldReceive('setex')
            ->once()
            ->andReturnUsing(function ($key, $ttl, $value) use (&$written) {
                $written = compact('key', 'ttl', 'value');

                return true;
            });

        $ticket = app(SessionTicketService::class)->issue($user, 'classic');

        $this->assertSame("habnut:ticket:{$ticket}", $written['key']);
        $this->assertSame("{$user->id}:classic", $written['value']);
        $this->assertSame(300, $written['ttl']);
    }

    /** @test */
    public function a_ticket_is_long_and_url_safe_so_it_survives_an_address_bar(): void
    {
        Redis::shouldReceive('setex')->andReturn(true);

        $ticket = app(SessionTicketService::class)->issue($this->tupci(), 'classic');

        $this->assertMatchesRegularExpression('/^[A-Za-z0-9_-]{43}$/', $ticket);
    }

    /** @test */
    public function two_tickets_are_never_the_same(): void
    {
        Redis::shouldReceive('setex')->andReturn(true);
        $tickets = app(SessionTicketService::class);
        $user = $this->tupci();

        $issued = collect(range(1, 50))->map(fn () => $tickets->issue($user, 'classic'));

        $this->assertCount(50, $issued->unique());
    }

    /** @test */
    public function an_unknown_world_falls_back_to_the_hotel_rather_than_failing(): void
    {
        $tickets = app(SessionTicketService::class);

        $this->assertSame('classic', $tickets->normaliseWorld('somewhere-else'));
        $this->assertSame('classic', $tickets->normaliseWorld(null));
        $this->assertSame('classic', $tickets->normaliseWorld(''));
        $this->assertSame('nutropolis', $tickets->normaliseWorld('NUTROPOLIS'));
        $this->assertSame('nutropolis', $tickets->normaliseWorld(' nutropolis '));
    }

    /** @test */
    public function tupci_is_sent_to_the_hotel_with_a_ticket_already_made(): void
    {
        $user = $this->tupci();

        $captured = null;
        Redis::shouldReceive('setex')
            ->once()
            ->andReturnUsing(function ($key, $ttl, $value) use (&$captured) {
                $captured = compact('key', 'value');

                return true;
            });

        $response = $this->actingAs($user)->get('/hotel');

        $response->assertRedirect();
        $target = $response->headers->get('Location');

        parse_str(parse_url($target, PHP_URL_QUERY) ?? '', $query);

        $this->assertStringStartsWith('/client/', $target);
        $this->assertSame('classic', $query['world']);
        $this->assertSame("habnut:ticket:{$query['ticket']}", $captured['key']);
        $this->assertSame("{$user->id}:classic", $captured['value']);
    }

    /** @test */
    public function tupci_can_ask_for_the_roleplay_city_instead(): void
    {
        $user = $this->tupci();

        $captured = null;
        Redis::shouldReceive('setex')
            ->once()
            ->andReturnUsing(function ($key, $ttl, $value) use (&$captured) {
                $captured = $value;

                return true;
            });

        $response = $this->actingAs($user)->get('/hotel?world=nutropolis');

        $response->assertRedirect();
        $this->assertStringContainsString('world=nutropolis', $response->headers->get('Location'));
        $this->assertSame("{$user->id}:nutropolis", $captured);
    }

    /** @test */
    public function a_signed_out_visitor_is_asked_to_make_an_account_rather_than_given_a_ticket(): void
    {
        Redis::shouldReceive('setex')->never();

        $this->get('/hotel')
            ->assertOk()
            ->assertSee('You need an account to come in.');
    }

    /** @test */
    public function a_suspended_player_is_told_why_and_given_no_ticket(): void
    {
        $user = $this->tupci();
        $user->bans()->create([
            'reason' => 'Being unpleasant in the lobby',
            'staff_id' => $user->id,
            'active' => true,
            'expires_at' => null,
        ]);

        Redis::shouldReceive('setex')->never();

        $this->actingAs($user)->get('/hotel')
            ->assertOk()
            ->assertSee('Being unpleasant in the lobby');
    }

    /** @test */
    public function the_api_hands_out_a_ticket_for_a_client_whose_own_has_run_out(): void
    {
        $user = $this->tupci();

        $captured = null;
        Redis::shouldReceive('setex')
            ->once()
            ->andReturnUsing(function ($key, $ttl, $value) use (&$captured) {
                $captured = compact('key', 'value');

                return true;
            });

        $response = $this->actingAs($user, 'sanctum')->postJson('/api/ticket');

        $response->assertOk()->assertJsonStructure(['ticket', 'world', 'expiresIn']);
        $ticket = $response->json('ticket');

        $this->assertSame("habnut:ticket:{$ticket}", $captured['key']);
        $this->assertSame("{$user->id}:classic", $captured['value']);
    }

    /** @test */
    public function a_suspended_player_gets_no_ticket_from_the_api_either(): void
    {
        $user = $this->tupci();
        $user->bans()->create([
            'reason' => 'Suspended',
            'staff_id' => $user->id,
            'active' => true,
            'expires_at' => null,
        ]);

        Redis::shouldReceive('setex')->never();

        $this->actingAs($user, 'sanctum')->postJson('/api/ticket')->assertForbidden();
    }
}
