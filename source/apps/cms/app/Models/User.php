<?php

namespace App\Models;

use App\Support\Imager;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;
use Illuminate\Support\Carbon;
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

    /**
     * The hotel's own account table.
     *
     * There is one account, not a website account and a hotel account. The
     * website used to keep its own `users` table, which meant registering here
     * created somebody the hotel had never heard of and the two could never be
     * reconciled — a player could sign up and then not get in.
     */
    protected $table = 'habnut_users';

    /** The hotel calls this member_since, and it is the same moment. */
    public const CREATED_AT = 'member_since';

    protected $fillable = [
        'username',
        'email',
        'password_hash',
        'rank',
        'motto',
        'figure',
        'credits',
        'diamonds',
        'nut_points',
        'seasonal_currency',
        'machine_id',
        'email_verified_at',
        'two_fa_secret',
        'two_fa_enabled',
        'last_login',
        'last_ip',
    ];

    protected $hidden = [
        'password_hash',
        'remember_token',
        'two_fa_secret',
        'machine_id',
    ];

    /**
     * The moment this account was made.
     *
     * The hotel's column is member_since, so there is no `created_at` to read.
     * Pages across the site ask for `created_at` because that is what every
     * other model here answers to; this keeps that one name working rather
     * than making each caller remember which table it came from.
     */
    public function getCreatedAtAttribute(): ?Carbon
    {
        return $this->member_since;
    }

    /**
     * Where the password lives.
     *
     * Laravel looks for a `password` column by default; the hotel stores it as
     * password_hash, and both sides have to agree or nobody can sign in
     * anywhere.
     */
    public function getAuthPassword(): string
    {
        return $this->password_hash;
    }

    protected function casts(): array
    {
        return [
            'email_verified_at' => 'datetime',
            'last_login' => 'datetime',
            'member_since' => 'datetime',
            'last_seen' => 'datetime',
            'two_fa_enabled' => 'boolean',
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
        return Imager::avatar($this->figure, $size, $direction);
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
