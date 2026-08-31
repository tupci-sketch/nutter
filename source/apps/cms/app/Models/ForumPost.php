<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

/**
 * One post in a thread.
 *
 * A removed post is hidden rather than deleted, and records who hid it and why,
 * so its author can be told what happened and a moderator's decision can be
 * reviewed later.
 */
class ForumPost extends Model
{
    use HasFactory;

    /**
     * Rows are written once and only ever flagged afterwards, so the hotel's
     * schema carries no updated_at for them.
     */
    public const UPDATED_AT = null;

    protected $table = 'habnut_forum_posts';

    protected $fillable = [
        'thread_id',
        'author_id',
        'body',
    ];

    protected function casts(): array
    {
        return [
            'hidden' => 'boolean',
            'created_at' => 'datetime',
            'edited_at' => 'datetime',
            'hidden_at' => 'datetime',
        ];
    }

    public function thread(): BelongsTo
    {
        return $this->belongsTo(ForumThread::class, 'thread_id');
    }

    public function author(): BelongsTo
    {
        return $this->belongsTo(User::class, 'author_id');
    }

    public function editor(): BelongsTo
    {
        return $this->belongsTo(User::class, 'edited_by_id');
    }

    public function reports(): HasMany
    {
        return $this->hasMany(ForumReport::class, 'post_id');
    }

    /** True when the post carries an edit somebody should be told about. */
    public function wasEdited(): bool
    {
        return $this->edited_at !== null;
    }
}
