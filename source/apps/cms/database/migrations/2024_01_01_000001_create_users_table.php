<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // The hotel's account table. In production the emulator's migrations
        // create this; here it is mirrored so the CMS is tested against the
        // columns it really queries rather than a second, parallel users table
        // that no player ever appears in.
        if (! Schema::hasTable('habnut_users')) {
            Schema::create('habnut_users', function (Blueprint $table) {
                $table->bigIncrements('id');
                $table->string('username', 25)->unique();
                $table->string('email', 254)->unique();
                $table->string('password_hash');
                $table->string('figure', 255)->default('');
                $table->char('gender', 1)->default('M');
                $table->string('motto', 190)->default('');
                $table->unsignedTinyInteger('rank')->default(1);
                $table->boolean('email_verified')->default(false);
                $table->timestamp('email_verified_at')->nullable();
                $table->string('two_fa_secret', 64)->nullable();
                $table->boolean('two_fa_enabled')->default(false);
                $table->unsignedInteger('credits')->default(0);
                $table->unsignedInteger('diamonds')->default(0);
                $table->unsignedInteger('nut_points')->default(0);
                $table->unsignedInteger('seasonal_currency')->default(0);
                $table->unsignedInteger('achievement_score')->default(0);
                $table->unsignedInteger('xp')->default(0);
                $table->boolean('online')->default(false);
                $table->string('membership_tier', 16)->default('none');
                $table->timestamp('membership_expiry')->nullable();
                $table->unsignedInteger('current_effect')->default(0);
                $table->string('machine_id', 128)->nullable();
                $table->string('last_ip', 45)->nullable();
                $table->timestamp('last_login')->nullable();
                $table->timestamp('last_seen')->nullable();
                $table->rememberToken();
                $table->timestamp('member_since')->useCurrent();
                $table->timestamp('updated_at')->useCurrent();
            });
        }

        Schema::create('password_reset_tokens', function (Blueprint $table) {
            $table->string('email')->primary();
            $table->string('token');
            $table->timestamp('created_at')->nullable();
        });

        Schema::create('sessions', function (Blueprint $table) {
            $table->string('id')->primary();
            $table->foreignId('user_id')->nullable()->index();
            $table->string('ip_address', 45)->nullable();
            $table->text('user_agent')->nullable();
            $table->longText('payload');
            $table->integer('last_activity')->index();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('sessions');
        Schema::dropIfExists('password_reset_tokens');
        Schema::dropIfExists('habnut_users');
    }
};
