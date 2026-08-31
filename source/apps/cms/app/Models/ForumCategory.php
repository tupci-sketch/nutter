<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;

/**
 * A public board.
 *
 * Boards are gated by rank rather than by a list of permissions: a board
 * everyone reads but only staff post to, and a board only staff can see at all,
 * are the same mechanism with different numbers.
 */
class ForumCategory extends Model
{
    use HasFactory;

    protected $table = 'habnut_forum_categories';

    protected $fillable = [
        'slug',
        'name',
        'description',
        'min_read_rank',
        'min_post_rank',
        'sort_order',
        'locked',
    ];

    protected function casts(): array
    {
        return [
            'locked' => 'boolean',
            'last_post_at' => 'datetime',
        ];
    }

    public function threads(): HasMany
    {
        return $this->hasMany(ForumThread::class, 'category_id');
    }

    /** True when this user may see the board at all. */
    public function readableBy(?User $user): bool
    {
        return ($user?->rank ?? 0) >= $this->min_read_rank;
    }

    /**
     * True when this user may start a thread here.
     *
     * A locked board still reads, so an archive stays browsable after it stops
     * taking new threads.
     */
    public function postableBy(?User $user): bool
    {
        if ($user === null || $this->locked) {
            return false;
        }

        return $user->rank >= $this->min_post_rank && $this->readableBy($user);
    }

    /** Boards this user may see, in display order. */
    public function scopeVisibleTo($query, ?User $user): mixed
    {
        return $query->where('min_read_rank', '<=', $user?->rank ?? 0)
            ->orderBy('sort_order')
            ->orderBy('id');
    }
}
