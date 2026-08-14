<?php

namespace Tests\Feature;

use Tests\TestCase;
use App\Models\User;
use App\Models\Ban;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;

class AuthTest extends TestCase
{
    use RefreshDatabase;

    private function createTupci(array $overrides = []): User
    {
        return User::factory()->create(array_merge([
            'username'           => 'tupci',
            'email'              => 'tupci@icloud.com',
            'password'           => Hash::make('testpassword123'),
            'rank'               => 1,
            'credits'            => 500,
            'diamonds'           => 10,
            'nut_points'         => 250,
            'two_factor_enabled' => false,
            'email_verified_at'  => now(),
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
            'email'    => 'tupci@icloud.com',
            'password' => 'testpassword123',
        ]);

        $response->assertRedirect(route('home'));
        $this->assertAuthenticated();
    }

    /** @test */
    public function tupci_login_fails_with_wrong_password(): void
    {
        $this->createTupci();

        $response = $this->post(route('login'), [
            'email'    => 'tupci@icloud.com',
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
            'user_id'    => $tupci->id,
            'active'     => true,
            'expires_at' => now()->addDays(30),
            'reason'     => 'Testing ban enforcement',
        ]);

        $response = $this->post(route('login'), [
            'email'    => 'tupci@icloud.com',
            'password' => 'testpassword123',
        ]);

        $response->assertSessionHasErrors('email');
        $this->assertGuest();
    }

    /** @test */
    public function tupci_with_2fa_enabled_redirects_to_2fa_after_login(): void
    {
        $this->createTupci([
            'two_factor_enabled' => true,
            'two_factor_secret'  => 'TESTSECRET123456',
        ]);

        $response = $this->post(route('login'), [
            'email'    => 'tupci@icloud.com',
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
        $tupci = $this->createTupci();
        $this->actingAs($tupci, 'sanctum');

        $response = $this->postJson(route('api.ticket'));

        $response->assertOk();
        $response->assertJsonStructure(['ticket']);
        $this->assertStringStartsWith('HNT-', $response->json('ticket'));
    }

    /** @test */
    public function session_ticket_is_stored_in_cache(): void
    {
        $tupci = $this->createTupci();
        $this->actingAs($tupci, 'sanctum');

        $response = $this->postJson(route('api.ticket'));
        $ticket   = $response->json('ticket');

        $this->assertEquals($tupci->id, cache()->get("ticket:{$ticket}"));
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
            'username'              => 'tupci',
            'email'                 => 'tupci@icloud.com',
            'password'              => 'securepassword123',
            'password_confirmation' => 'securepassword123',
        ]);

        $response->assertRedirect();
        $this->assertDatabaseHas('users', [
            'username' => 'tupci',
            'email'    => 'tupci@icloud.com',
        ]);
    }

    /** @test */
    public function tupci_cannot_register_with_duplicate_username(): void
    {
        $this->createTupci();

        $response = $this->post(route('register'), [
            'username'              => 'tupci',
            'email'                 => 'different@test.com',
            'password'              => 'securepassword123',
            'password_confirmation' => 'securepassword123',
        ]);

        $response->assertSessionHasErrors('username');
    }
}
