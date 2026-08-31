<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/** A player's report of a post, waiting for a forum moderator to look at it. */
class ForumReport extends Model
{
    use HasFactory;

    /**
     * Rows are written once and only ever flagged afterwards, so the hotel's
     * schema carries no updated_at for them.
     */
    public const UPDATED_AT = null;

    protected $table = 'habnut_forum_reports';

    protected $fillable = [
        'post_id',
        'reporter_id',
        'reason',
    ];

    protected function casts(): array
    {
        return [
            'created_at' => 'datetime',
            'handled_at' => 'datetime',
        ];
    }

    public function post(): BelongsTo
    {
        return $this->belongsTo(ForumPost::class, 'post_id');
    }

    public function reporter(): BelongsTo
    {
        return $this->belongsTo(User::class, 'reporter_id');
    }

    public function handler(): BelongsTo
    {
        return $this->belongsTo(User::class, 'handled_by_id');
    }
}
