<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class DccAccessTest extends TestCase
{
    use RefreshDatabase;

    private function makeUser(int $rank, string $suffix = ''): User
    {
        return User::factory()->create([
            'username' => "tupci{$suffix}",
            'email' => "tupci{$suffix}@test.com",
            'password' => Hash::make('password'),
            'rank' => $rank,
            'two_factor_enabled' => false,
            'email_verified_at' => now(),
        ]);
    }

    /** @test */
    public function regular_user_tupci_cannot_access_dcc(): void
    {
        $tupci = $this->makeUser(1);
        $this->actingAs($tupci);

        $response = $this->get(route('dcc.dashboard'));
        $response->assertStatus(403);
    }

    /** @test */
    public function rank_3_user_cannot_access_dcc(): void
    {
        $tupci = $this->makeUser(3, '_rank3');
        $this->actingAs($tupci);

        $response = $this->get(route('dcc.dashboard'));
        $response->assertStatus(403);
    }

    /** @test */
    public function tupci_as_staff_rank_4_can_access_dcc(): void
    {
        $tupci = $this->makeUser(4, '_staff');
        $this->actingAs($tupci);

        $response = $this->get(route('dcc.dashboard'));
        $response->assertStatus(200);
    }

    /** @test */
    public function tupci_as_admin_rank_7_can_access_dcc(): void
    {
        $tupci = $this->makeUser(7, '_admin');
        $this->actingAs($tupci);

        $response = $this->get(route('dcc.dashboard'));
        $response->assertStatus(200);
    }

    /** @test */
    public function unauthenticated_request_redirects_to_login(): void
    {
        $response = $this->get(route('dcc.dashboard'));
        $response->assertRedirect(route('login'));
    }

    /** @test */
    public function tupci_admin_can_view_users_list(): void
    {
        $tupci = $this->makeUser(7, '_admin');
        $this->actingAs($tupci);

        $response = $this->get(route('dcc.users.index'));
        $response->assertStatus(200);
    }

    /** @test */
    public function tupci_admin_can_view_user_detail(): void
    {
        $tupci = $this->makeUser(7, '_admin');
        $subject = $this->makeUser(1, '_subject');
        $this->actingAs($tupci);

        $response = $this->get(route('dcc.users.show', $subject->id));
        $response->assertStatus(200);
    }

    /** @test */
    public function tupci_admin_cannot_elevate_rank_to_own_level(): void
    {
        $tupci = $this->makeUser(7, '_admin');
        $subject = $this->makeUser(1, '_target');
        $this->actingAs($tupci);

        $response = $this->put(route('dcc.users.rank', $subject->id), [
            'rank' => 7, // equal to actor rank — should be rejected
        ]);

        $response->assertSessionHasErrors('rank');
        $this->assertEquals(1, $subject->fresh()->rank);
    }

    /** @test */
    public function tupci_admin_can_assign_lower_rank(): void
    {
        $tupci = $this->makeUser(7, '_admin');
        $subject = $this->makeUser(1, '_target2');
        $this->actingAs($tupci);

        $response = $this->put(route('dcc.users.rank', $subject->id), [
            'rank' => 4,
        ]);

        $response->assertRedirect();
        $this->assertEquals(4, $subject->fresh()->rank);
    }

    /** @test */
    public function dcc_economy_credit_adjustment_is_audit_logged(): void
    {
        $tupci = $this->makeUser(7, '_admin');
        $subject = $this->makeUser(1, '_rich');
        $this->actingAs($tupci);

        $this->put(route('dcc.users.credits', $subject->id), [
            'amount' => 100,
            'reason' => 'Test grant for tupci subject',
        ]);

        $this->assertDatabaseHas('habnut_audit_logs', [
            'actor_user_id' => $tupci->id,
            'action' => 'credits_adjust',
            'target_type' => 'user',
            'target_id' => $subject->id,
        ]);
    }
}
