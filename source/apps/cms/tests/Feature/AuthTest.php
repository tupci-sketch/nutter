<?php

namespace Tests\Feature;

use App\Models\Ban;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Redis;
use Tests\TestCase;

class AuthTest extends TestCase
{
    use RefreshDatabase;

    private function createTupci(array $overrides = []): User
    {
        return User::factory()->create(array_merge([
            'username' => 'tupci',
            'email' => 'tupci@icloud.com',
            'password_hash' => Hash::make('testpassword123'),
            'rank' => 1,
            'credits' => 500,
            'diamonds' => 10,
            'nut_points' => 250,
            'two_fa_enabled' => false,
            'email_verified_at' => now(),
        ], $overrides));
    }

    /** @test */
    public function login_page_loads(): void
    {
        $response = $this->get(route('login'));
        $response->assertStatus(200);
        $response->assertSee('Login');
    }

    /** @test */
    public function tupci_can_log_in_with_correct_credentials(): void
    {
        $this->createTupci();

        $response = $this->post(route('login'), [
            'email' => 'tupci@icloud.com',
            'password' => 'testpassword123',
        ]);

        $response->assertRedirect(route('home'));
        $this->assertAuthenticated();
    }

    /**
     * @test
     *
     * A hash made at a cost other than the configured one — every seeded
     * account is one — is rehashed as the player signs in, and the new hash
     * has to land in password_hash. Laravel's default target is a 'password'
     * column, which does not exist, so signing in failed at exactly that
     * point. The other tests hash at the configured cost and never got there.
     */
    public function a_password_hashed_at_another_cost_signs_in_and_is_upgraded(): void
    {
        $old = password_hash('testpassword123', PASSWORD_BCRYPT, ['cost' => 5]);
        $this->createTupci(['password_hash' => $old]);

        $response = $this->post(route('login'), [
            'email' => 'tupci@icloud.com',
            'password' => 'testpassword123',
        ]);

        $response->assertRedirect(route('home'));
        $this->assertAuthenticated();

        $stored = User::where('email', 'tupci@icloud.com')->value('password_hash');
        $this->assertNotSame($old, $stored, 'the hash was not upgraded');
        $this->assertTrue(Hash::check('testpassword123', $stored));
        $this->assertFalse(Hash::needsRehash($stored));
    }

    /** @test */
    public function tupci_login_fails_with_wrong_password(): void
    {
        $this->createTupci();

        $response = $this->post(route('login'), [
            'email' => 'tupci@icloud.com',
            'password' => 'wrongpassword',
        ]);

        $response->assertSessionHasErrors('email');
        $this->assertGuest();
    }

    /** @test */
    public function banned_tupci_cannot_log_in(): void
    {
        $tupci = $this->createTupci();

        Ban::factory()->create([
            'user_id' => $tupci->id,
            'expires_at' => now()->addDays(30),
            'reason' => 'Testing ban enforcement',
        ]);

        $response = $this->post(route('login'), [
            'email' => 'tupci@icloud.com',
            'password' => 'testpassword123',
        ]);

        $response->assertSessionHasErrors('email');
        $this->assertGuest();
    }

    /** @test */
    public function tupci_with_2fa_enabled_redirects_to_2fa_after_login(): void
    {
        $this->createTupci([
            'two_fa_enabled' => true,
            'two_fa_secret' => 'TESTSECRET123456',
        ]);

        $response = $this->post(route('login'), [
            'email' => 'tupci@icloud.com',
            'password' => 'testpassword123',
        ]);

        $response->assertRedirect(route('2fa.show'));
    }

    /** @test */
    public function tupci_can_log_out(): void
    {
        $tupci = $this->createTupci();
        $this->actingAs($tupci);

        $response = $this->post(route('logout'));

        $response->assertRedirect(route('login'));
        $this->assertGuest();
    }

    /** @test */
    public function ticket_endpoint_requires_authentication(): void
    {
        $response = $this->postJson(route('api.ticket'));
        $response->assertUnauthorized();
    }

    /** @test */
    public function tupci_can_obtain_a_session_ticket(): void
    {
        Redis::shouldReceive('setex')->once()->andReturn(true);

        $tupci = $this->createTupci();
        $this->actingAs($tupci, 'sanctum');

        $response = $this->postJson(route('api.ticket'));

        $response->assertOk();
        $response->assertJsonStructure(['ticket', 'world', 'expiresIn']);
        $this->assertNotEmpty($response->json('ticket'));
    }

    /**
     * Where the ticket goes is the hotel's business, not the cache's.
     *
     * Covered in detail by HotelHandoffTest, which asserts the exact key and
     * value the hotel reads; this is only here to show the endpoint writes one.
     *
     * @test
     */
    public function session_ticket_is_handed_to_the_hotel(): void
    {
        $written = false;
        Redis::shouldReceive('setex')
            ->once()
            ->andReturnUsing(function () use (&$written) {
                $written = true;

                return true;
            });

        $tupci = $this->createTupci();
        $this->actingAs($tupci, 'sanctum');

        $this->postJson(route('api.ticket'))->assertOk();

        $this->assertTrue($written, 'The ticket was never written anywhere the hotel can see it.');
    }

    /** @test */
    public function registration_page_loads(): void
    {
        $response = $this->get(route('register'));
        $response->assertStatus(200);
    }

    /** @test */
    public function tupci_can_register_a_new_account(): void
    {
        $response = $this->post(route('register'), [
            'username' => 'tupci',
            'email' => 'tupci@icloud.com',
            'password' => 'securepassword123',
            'password_confirmation' => 'securepassword123',
        ]);

        $response->assertRedirect();
        $this->assertDatabaseHas('habnut_users', [
            'username' => 'tupci',
            'email' => 'tupci@icloud.com',
        ]);
    }

    /** @test */
    public function tupci_cannot_register_with_duplicate_username(): void
    {
        $this->createTupci();

        $response = $this->post(route('register'), [
            'username' => 'tupci',
            'email' => 'different@test.com',
            'password' => 'securepassword123',
            'password_confirmation' => 'securepassword123',
        ]);

        $response->assertSessionHasErrors('username');
    }
}
