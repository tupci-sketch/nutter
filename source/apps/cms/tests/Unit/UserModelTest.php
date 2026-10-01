<?php

namespace Tests\Unit;

use App\Models\Ban;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class UserModelTest extends TestCase
{
    use RefreshDatabase;

    private function makeTupci(array $overrides = []): User
    {
        return User::factory()->create(array_merge([
            'username' => 'tupci',
            'email' => 'tupci@icloud.com',
            'rank' => 1,
            'credits' => 500,
            'diamonds' => 10,
        ], $overrides));
    }

    /** @test */
    public function tupci_starts_as_a_regular_user(): void
    {
        $tupci = $this->makeTupci(['rank' => 1]);

        $this->assertFalse($tupci->isStaff());
        $this->assertFalse($tupci->isAdmin());
    }

    /** @test */
    public function tupci_becomes_staff_at_rank_4(): void
    {
        $tupci = $this->makeTupci(['rank' => 4]);

        $this->assertTrue($tupci->isStaff());
        $this->assertFalse($tupci->isAdmin());
    }

    /** @test */
    public function tupci_is_admin_at_rank_7(): void
    {
        $tupci = $this->makeTupci(['rank' => 7]);

        $this->assertTrue($tupci->isStaff());
        $this->assertTrue($tupci->isAdmin());
    }

    /** @test */
    public function tupci_has_no_active_ban_by_default(): void
    {
        $tupci = $this->makeTupci();

        $this->assertNull($tupci->activeBan());
    }

    /** @test */
    public function tupci_active_ban_blocks_account(): void
    {
        $tupci = $this->makeTupci();

        Ban::factory()->create([
            'user_id' => $tupci->id,
            'active' => true,
            'expires_at' => now()->addDays(7),
            'reason' => 'Test ban',
        ]);

        $this->assertNotNull($tupci->fresh()->activeBan());
    }

    /** @test */
    public function tupci_expired_ban_is_not_active(): void
    {
        $tupci = $this->makeTupci();

        Ban::factory()->create([
            'user_id' => $tupci->id,
            'active' => true,
            'expires_at' => now()->subMinutes(1),
            'reason' => 'Expired ban',
        ]);

        $this->assertNull($tupci->fresh()->activeBan());
    }

    /** @test */
    public function tupci_default_currency_values_are_integers(): void
    {
        $tupci = $this->makeTupci();

        $this->assertIsInt($tupci->credits);
        $this->assertIsInt($tupci->diamonds);
        $this->assertIsInt($tupci->nut_points);
    }

    /** @test */
    public function two_factor_enabled_casts_to_boolean(): void
    {
        $tupci = $this->makeTupci(['two_fa_enabled' => false]);
        $this->assertIsBool($tupci->two_fa_enabled);
    }
}
