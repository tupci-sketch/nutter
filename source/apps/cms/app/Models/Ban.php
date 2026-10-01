<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * A ban, as the hotel records it.
 *
 * There used to be two ban tables: a `bans` table the website wrote to and the
 * hotel's `habnut_bans`, which the hotel actually checks at the door. A
 * moderator could ban somebody on the website and watch them walk straight back
 * into the hotel, because the two halves were never looking at the same rows.
 * This is the hotel's table.
 *
 * The hotel has no `active` column: a ban is in force while it has not been
 * lifted and has not run out, which is the same question asked of the same
 * three columns on both sides.
 */
class Ban extends Model
{
    use HasFactory;

    protected $table = 'habnut_bans';

    /** The hotel stamps created_at itself and has no updated_at. */
    public const UPDATED_AT = null;

    protected $fillable = [
        'user_id',
        'banned_by_id',
        'reason',
        'ban_type',
        'ip_address',
        'machine_id_hash',
        'expires_at',
        'lifted_at',
        'lifted_by_id',
    ];

    protected function casts(): array
    {
        return [
            'expires_at' => 'datetime',
            'lifted_at' => 'datetime',
            'created_at' => 'datetime',
        ];
    }

    /**
     * Bans that are in force right now.
     *
     * Exactly the question the hotel asks when somebody knocks: not lifted,
     * and either permanent or not yet run out.
     */
    public function scopeActive(Builder $query): Builder
    {
        return $query
            ->whereNull('lifted_at')
            ->where(fn ($q) => $q->whereNull('expires_at')->orWhere('expires_at', '>', now()));
    }

    /** Bans that have been lifted or have run out. */
    public function scopeInactive(Builder $query): Builder
    {
        return $query
            ->whereNotNull('lifted_at')
            ->orWhere(fn ($q) => $q->whereNotNull('expires_at')->where('expires_at', '<=', now()));
    }

    /** Whether this particular ban is in force. */
    public function isActive(): bool
    {
        return $this->lifted_at === null
            && ($this->expires_at === null || $this->expires_at->isFuture());
    }

    /** Lift this ban, recording who did it. */
    public function lift(int $staffId): void
    {
        $this->forceFill(['lifted_at' => now(), 'lifted_by_id' => $staffId])->save();
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class, 'user_id');
    }

    /** The staff member who applied it. Null for one the hotel applied itself. */
    public function staff(): BelongsTo
    {
        return $this->belongsTo(User::class, 'banned_by_id');
    }
}
