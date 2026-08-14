<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class Ban extends Model
{
    use HasFactory;

    protected $fillable = [
        'user_id',
        'reason',
        'banned_by_username',
        'active',
        'expires_at',
    ];

    protected function casts(): array
    {
        return [
            'active'     => 'boolean',
            'expires_at' => 'datetime',
        ];
    }
}
