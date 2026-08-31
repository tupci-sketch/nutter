<?php

namespace App\Models;

use App\Support\Imager;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;
use Laravel\Sanctum\HasApiTokens;
use Spatie\Permission\Traits\HasRoles;

class User extends Authenticatable
{
    use HasApiTokens, HasFactory, HasRoles, Notifiable;

    /** Rank thresholds. Staff begins at moderator, full access at admin. */
    public const RANK_MEMBER = 1;

    public const RANK_VIP = 2;

    public const RANK_HELPER = 3;

    public const RANK_MODERATOR = 4;

    public const RANK_ADMIN = 7;

    /** Display names for each rank threshold, highest first. */
    public const RANK_NAMES = [
        self::RANK_ADMIN => 'Administrator',
        self::RANK_MODERATOR => 'Moderator',
        self::RANK_HELPER => 'Helper',
        self::RANK_VIP => 'VIP',
        self::RANK_MEMBER => 'Member',
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
            'last_login' => 'datetime',
            'two_factor_enabled' => 'boolean',
            'rank' => 'integer',
            'online' => 'boolean',
            'achievement_score' => 'integer',
            'credits' => 'integer',
            'diamonds' => 'integer',
            'nut_points' => 'integer',
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
     * The picture comes from the hotel's own imager, which draws it from the
     * same asset pack the game client uses. A hotel that has turned the imager
     * off gets null and the view falls back to a monogram, so the page is still
     * complete.
     *
     * A size of 's' asks for the small head used in lists; 'm' and 'l' are the
     * full figure at one and two pixels per pixel.
     */
    public function avatarUrl(string $size = 'm', int $direction = 2): ?string
    {
        return Imager::avatar($this->look, $size, $direction);
    }

    /** URL of a badge picture, whether it is a named badge or a group code. */
    public function badgeUrl(string $badge): ?string
    {
        return Imager::badge($badge);
    }

    /** True when this user is a staff member and currently in the hotel. */
    public function isStaffOnline(): bool
    {
        return $this->online && $this->isStaff();
    }

    public function bans(): HasMany
    {
        return $this->hasMany(Ban::class);
    }

    public function activeBan(): ?Ban
    {
        return $this->bans()
            ->where('active', true)
            ->where(fn ($q) => $q->whereNull('expires_at')->orWhere('expires_at', '>', now()))
            ->latest()
            ->first();
    }
}
