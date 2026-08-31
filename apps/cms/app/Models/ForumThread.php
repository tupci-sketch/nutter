<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

/**
 * A thread on a public board or in a group's forum.
 *
 * Both live in one table so a group's forum is the same thing whether it is
 * read on the website or inside the hotel. Exactly one of category_id and
 * group_id is set; the database enforces that, because a thread with neither
 * would be unreachable and one with both would appear twice.
 */
class ForumThread extends Model
{
    use HasFactory;

    protected $table = 'habnut_forum_threads';

    protected $fillable = [
        'category_id',
        'group_id',
        'author_id',
        'title',
    ];

    protected function casts(): array
    {
        return [
            'pinned' => 'boolean',
            'locked' => 'boolean',
            'hidden' => 'boolean',
            'created_at' => 'datetime',
            'last_reply_at' => 'datetime',
        ];
    }

    public function category(): BelongsTo
    {
        return $this->belongsTo(ForumCategory::class, 'category_id');
    }

    public function author(): BelongsTo
    {
        return $this->belongsTo(User::class, 'author_id');
    }

    public function lastPoster(): BelongsTo
    {
        return $this->belongsTo(User::class, 'last_poster_id');
    }

    public function posts(): HasMany
    {
        return $this->hasMany(ForumPost::class, 'thread_id');
    }

    /** Posts anyone may read, oldest first, as a thread is read top to bottom. */
    public function visiblePosts(): HasMany
    {
        return $this->posts()->where('hidden', false)->orderBy('created_at');
    }

    /** True when this thread belongs to a group rather than a public board. */
    public function isGroupThread(): bool
    {
        return $this->group_id !== null;
    }

    /** Threads shown on a board: pinned first, then by most recent reply. */
    public function scopeListed($query): mixed
    {
        return $query->where('hidden', false)
            ->orderByDesc('pinned')
            ->orderByDesc('last_reply_at');
    }
}
