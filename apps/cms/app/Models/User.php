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

    /** Rank thresholds. Staff begins at moderator, full access at admin. */
    public const RANK_MEMBER    = 1;
    public const RANK_VIP       = 2;
    public const RANK_HELPER    = 3;
    public const RANK_MODERATOR = 4;
    public const RANK_ADMIN     = 7;

    /** Display names for each rank threshold, highest first. */
    public const RANK_NAMES = [
        self::RANK_ADMIN     => 'Administrator',
        self::RANK_MODERATOR => 'Moderator',
        self::RANK_HELPER    => 'Helper',
        self::RANK_VIP       => 'VIP',
        self::RANK_MEMBER    => 'Member',
    ];

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
            'online'            => 'boolean',
            'achievement_score' => 'integer',
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
        return $this->rank >= self::RANK_ADMIN;
    }

    /** The display name for this user's rank. */
    public function rankName(): string
    {
        foreach (self::RANK_NAMES as $threshold => $name) {
            if ($this->rank >= $threshold) {
                return $name;
            }
        }
        return 'Member';
    }

    /**
     * URL of this user's rendered figure.
     *
     * When an imager service is configured the figure string is rendered by it;
     * otherwise this returns null and the view falls back to a monogram, so a
     * hotel without an imager still shows a complete page.
     */
    public function avatarUrl(string $size = 'm'): ?string
    {
        $imager = config('habnut.imager_url');
        if (! $imager) {
            return null;
        }
        return rtrim($imager, '/') . '/?figure=' . urlencode($this->look) . '&size=' . $size;
    }

    /** True when this user is a staff member and currently in the hotel. */
    public function isStaffOnline(): bool
    {
        return $this->online && $this->isStaff();
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
