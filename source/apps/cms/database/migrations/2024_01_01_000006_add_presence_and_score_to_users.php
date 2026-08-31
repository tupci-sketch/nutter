<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Presence and achievement score on the CMS user record.
 *
 * The hotel page shows how many players are in the hotel, which staff are
 * online, and a small leaderboard. All three read from these columns, which the
 * emulator maintains as players connect and progress.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->boolean('online')->default(false)->index();
            $table->unsignedInteger('achievement_score')->default(0)->index();
        });
    }

    public function down(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->dropColumn(['online', 'achievement_score']);
        });
    }
};
