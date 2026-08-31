<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * The automated moderation tables the staff tools read.
 *
 * The emulator owns this schema; these definitions exist so the review queue
 * can be tested against the shape it actually queries.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('habnut_mutes', function (Blueprint $table) {
            // An automatic mute has no staff member behind it, so the column
            // that names one becomes optional and the row records where the
            // mute came from instead.
            $table->unsignedBigInteger('muted_by_id')->nullable()->change();
            $table->string('source', 16)->default('staff');
        });

        Schema::create('habnut_content_rules', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->string('category', 32);
            $table->string('label', 128);
            $table->string('pattern', 512);
            $table->string('exempt_pattern', 512)->nullable();
            $table->string('match_mode', 16)->default('words');
            $table->string('action', 8)->default('mute');
            $table->unsignedTinyInteger('severity')->default(3);
            $table->unsignedSmallInteger('mute_minutes')->default(1440);
            $table->boolean('enabled')->default(true);
            $table->unsignedBigInteger('created_by_id')->nullable();
            $table->timestamps();
        });

        Schema::create('habnut_auto_mutes', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('user_id');
            $table->unsignedBigInteger('mute_id')->nullable();
            $table->unsignedBigInteger('rule_id')->nullable();
            $table->string('category', 32);
            $table->text('message');
            $table->unsignedBigInteger('room_id')->nullable();
            $table->string('status', 20)->default('pending_review');
            $table->unsignedBigInteger('reviewed_by_id')->nullable();
            $table->timestamp('reviewed_at')->nullable();
            $table->string('review_notes', 512)->nullable();
            $table->timestamp('created_at')->useCurrent();
            $table->index(['status', 'created_at']);
        });

        Schema::create('habnut_mute_help_requests', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('auto_mute_id');
            $table->unsignedBigInteger('user_id');
            $table->string('message', 512);
            $table->unsignedBigInteger('handled_by_id')->nullable();
            $table->timestamp('handled_at')->nullable();
            $table->string('response', 512)->nullable();
            $table->timestamp('created_at')->useCurrent();
            $table->index(['handled_at', 'created_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('habnut_mute_help_requests');
        Schema::dropIfExists('habnut_auto_mutes');
        Schema::dropIfExists('habnut_content_rules');
    }
};
