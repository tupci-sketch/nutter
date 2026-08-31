<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * A forum role held by one player.
 *
 * Forum standing is deliberately separate from hotel rank. A player can be
 * trusted to run the trading board without being handed moderator powers inside
 * the hotel, and a hotel moderator is not automatically the right person to run
 * a group's forum.
 */
class ForumModerator extends Model
{
    use HasFactory;

    public $timestamps = false;

    protected $table = 'habnut_forum_moderators';

    protected $fillable = [
        'user_id',
        'scope',
        'scope_id',
        'role',
        'granted_by_id',
    ];

    protected function casts(): array
    {
        return [
            'granted_at' => 'datetime',
        ];
    }

    /** Every board. */
    public const SCOPE_GLOBAL = 'global';

    /** One public board. */
    public const SCOPE_CATEGORY = 'category';

    /** One group's forum. */
    public const SCOPE_GROUP = 'group';

    /** Hides, locks and pins. */
    public const ROLE_MODERATOR = 'moderator';

    /** Also edits others' posts, moves threads and appoints moderators. */
    public const ROLE_ADMINISTRATOR = 'administrator';

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class, 'user_id');
    }

    public function grantor(): BelongsTo
    {
        return $this->belongsTo(User::class, 'granted_by_id');
    }
}
