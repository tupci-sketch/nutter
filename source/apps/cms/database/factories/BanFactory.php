<?php

namespace Database\Factories;

use App\Models\Ban;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;

class BanFactory extends Factory
{
    protected $model = Ban::class;

    public function definition(): array
    {
        return [
            'user_id' => User::factory(),
            'banned_by_id' => null,
            'reason' => fake()->sentence(),
            'ban_type' => 'account',
            'expires_at' => now()->addDays(7),
            'lifted_at' => null,
        ];
    }

    public function permanent(): static
    {
        return $this->state(['expires_at' => null]);
    }

    public function expired(): static
    {
        return $this->state(['expires_at' => now()->subDay()]);
    }

    /** A ban a moderator has since lifted. */
    public function lifted(): static
    {
        return $this->state(['lifted_at' => now()->subHour()]);
    }
}
