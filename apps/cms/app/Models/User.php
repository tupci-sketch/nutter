<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;
use Laravel\Sanctum\HasApiTokens;
use Spatie\Permission\Traits\HasRoles;

class User extends Authenticatable
{
    use HasApiTokens, HasFactory, Notifiable, HasRoles;

    protected $fillable = [
        'username',
        'email',
        'password',
        'rank',
        'motto',
        'look',
        'credits',
        'diamonds',
        'nut_points',
        'seasonal_currency',
        'machine_id',
        'email_verified_at',
        'two_factor_secret',
        'two_factor_enabled',
        'last_login',
        'last_ip',
    ];

    protected $hidden = [
        'password',
        'remember_token',
        'two_factor_secret',
        'machine_id',
    ];

    protected function casts(): array
    {
        return [
            'email_verified_at' => 'datetime',
            'last_login'        => 'datetime',
            'two_factor_enabled'=> 'boolean',
            'rank'              => 'integer',
            'credits'           => 'integer',
            'diamonds'          => 'integer',
            'nut_points'        => 'integer',
            'seasonal_currency' => 'integer',
        ];
    }

    public function isStaff(): bool
    {
        return $this->rank >= 4;
    }

    public function isAdmin(): bool
    {
        return $this->rank >= 7;
    }

    public function bans(): \Illuminate\Database\Eloquent\Relations\HasMany
    {
        return $this->hasMany(Ban::class);
    }

    public function activeBan(): ?Ban
    {
        return $this->bans()
            ->where('active', true)
            ->where(fn($q) => $q->whereNull('expires_at')->orWhere('expires_at', '>', now()))
            ->latest()
            ->first();
    }
}
