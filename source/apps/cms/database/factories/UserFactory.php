<?php

namespace Database\Factories;

use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Str;

class UserFactory extends Factory
{
    protected $model = User::class;

    public function definition(): array
    {
        return [
            'username' => fake()->unique()->userName(),
            'email' => fake()->unique()->safeEmail(),
            'password' => Hash::make('password'),
            'rank' => 1,
            'motto' => fake()->sentence(4),
            'look' => 'hd-180-1.ch-210-66.lg-270-110',
            'credits' => fake()->numberBetween(0, 1000),
            'diamonds' => fake()->numberBetween(0, 50),
            'nut_points' => fake()->numberBetween(0, 500),
            'seasonal_currency' => 0,
            'two_factor_enabled' => false,
            'email_verified_at' => now(),
            'remember_token' => Str::random(10),
        ];
    }

    public function unverified(): static
    {
        return $this->state(['email_verified_at' => null]);
    }

    public function staff(): static
    {
        return $this->state(['rank' => 5]);
    }

    public function admin(): static
    {
        return $this->state(['rank' => 7]);
    }
}
