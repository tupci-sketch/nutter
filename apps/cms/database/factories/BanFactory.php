<?php

namespace Database\Factories;

use App\Models\Ban;
use Illuminate\Database\Eloquent\Factories\Factory;

class BanFactory extends Factory
{
    protected $model = Ban::class;

    public function definition(): array
    {
        return [
            'user_id'            => \App\Models\User::factory(),
            'reason'             => fake()->sentence(),
            'banned_by_username' => 'admin',
            'active'             => true,
            'expires_at'         => now()->addDays(7),
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
}
