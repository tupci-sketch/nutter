<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * The hotel tables the CMS reads but had no local definition for.
 *
 * The emulator owns this schema; these definitions exist so the CMS can be
 * tested against the shape it actually queries. Several pages read tables that
 * were never mirrored here, which meant those pages could not be covered by a
 * test at all — a query against a table the test database lacks throws before
 * it can assert anything.
 */
return new class extends Migration
{
    public function up(): void
    {
        $this->alignGroupsAndRooms();
        $this->createForumTables();
        $this->createSocialAndProgressionTables();
        $this->createModerationTables();
        $this->createHotelActivityTables();
    }

    /**
     * Brings the group and room mirrors in line with the hotel's own schema.
     *
     * Group ranks are names, not numbers, and the forum needs a group's forum
     * mode to decide who may read and post.
     */
    private function alignGroupsAndRooms(): void
    {
        Schema::table('habnut_groups', function (Blueprint $table) {
            $table->string('description', 512)->default('');
            $table->string('access_mode', 16)->default('open');
            $table->string('forum_mode', 16)->default('open');
            $table->unsignedInteger('member_count')->default(1);
            $table->unsignedBigInteger('room_id')->nullable();
        });

        Schema::table('habnut_group_members', function (Blueprint $table) {
            $table->string('rank', 16)->default('member')->change();
        });

        Schema::table('habnut_rooms', function (Blueprint $table) {
            $table->string('description', 512)->default('');
            $table->unsignedInteger('user_count')->default(0);
        });
    }

    private function createForumTables(): void
    {
        Schema::create('habnut_forum_categories', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->string('slug', 64)->unique();
            $table->string('name', 96);
            $table->string('description', 512)->default('');
            $table->unsignedTinyInteger('min_read_rank')->default(0);
            $table->unsignedTinyInteger('min_post_rank')->default(1);
            $table->unsignedSmallInteger('sort_order')->default(0);
            $table->boolean('locked')->default(false);
            $table->unsignedInteger('thread_count')->default(0);
            $table->unsignedInteger('post_count')->default(0);
            $table->unsignedBigInteger('last_thread_id')->nullable();
            $table->timestamp('last_post_at')->nullable();
            $table->timestamps();
        });

        Schema::create('habnut_forum_threads', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('group_id')->nullable();
            $table->unsignedBigInteger('category_id')->nullable();
            $table->unsignedBigInteger('author_id');
            $table->string('title', 255);
            $table->unsignedInteger('reply_count')->default(0);
            $table->unsignedInteger('views')->default(0);
            $table->boolean('pinned')->default(false);
            $table->boolean('locked')->default(false);
            $table->boolean('hidden')->default(false);
            $table->unsignedBigInteger('last_post_id')->nullable();
            $table->unsignedBigInteger('last_poster_id')->nullable();
            $table->timestamp('created_at')->useCurrent();
            $table->timestamp('updated_at')->useCurrent();
            $table->timestamp('last_reply_at')->useCurrent();
            $table->index(['category_id', 'hidden', 'pinned', 'last_reply_at']);
            $table->index(['group_id', 'hidden', 'pinned', 'last_reply_at']);
        });

        Schema::create('habnut_forum_posts', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('thread_id');
            $table->unsignedBigInteger('author_id');
            $table->text('body');
            $table->boolean('hidden')->default(false);
            $table->timestamp('created_at')->useCurrent();
            $table->timestamp('hidden_at')->nullable();
            $table->timestamp('edited_at')->nullable();
            $table->unsignedBigInteger('edited_by_id')->nullable();
            $table->unsignedBigInteger('hidden_by_id')->nullable();
            $table->string('hidden_reason', 255)->nullable();
            $table->index(['thread_id', 'hidden', 'created_at']);
        });

        Schema::create('habnut_forum_moderators', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('user_id');
            $table->string('scope', 16)->default('category');
            $table->unsignedBigInteger('scope_id')->nullable();
            $table->string('role', 16)->default('moderator');
            $table->unsignedBigInteger('granted_by_id');
            $table->timestamp('granted_at')->useCurrent();
            $table->unique(['user_id', 'scope', 'scope_id']);
        });

        Schema::create('habnut_forum_reports', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('post_id');
            $table->unsignedBigInteger('reporter_id');
            $table->string('reason', 512);
            $table->string('status', 16)->default('open');
            $table->unsignedBigInteger('handled_by_id')->nullable();
            $table->timestamp('handled_at')->nullable();
            $table->string('notes', 512)->nullable();
            $table->timestamp('created_at')->useCurrent();
            $table->unique(['post_id', 'reporter_id']);
        });

        Schema::create('habnut_forum_subscriptions', function (Blueprint $table) {
            $table->unsignedBigInteger('thread_id');
            $table->unsignedBigInteger('user_id');
            $table->timestamp('last_read_at')->useCurrent();
            $table->boolean('notify')->default(true);
            $table->timestamp('created_at')->useCurrent();
            $table->primary(['thread_id', 'user_id']);
        });
    }

    private function createSocialAndProgressionTables(): void
    {
        Schema::create('habnut_friends', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('user_a');
            $table->unsignedBigInteger('user_b');
            $table->boolean('accepted')->default(true);
            $table->timestamp('created_at')->useCurrent();
            $table->unique(['user_a', 'user_b']);
        });

        Schema::create('habnut_badges', function (Blueprint $table) {
            $table->string('id', 32)->primary();
            $table->string('code', 64)->unique();
            $table->string('name', 128);
            $table->string('description', 512)->default('');
            $table->string('sprite_id', 128)->default('');
            $table->string('image_url', 512)->default('');
            $table->string('category', 32)->default('general');
            $table->boolean('is_achievement_badge')->default(false);
        });

        Schema::create('habnut_user_badges', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('user_id');
            $table->string('badge_code', 64);
            $table->unsignedTinyInteger('slot_index')->nullable();
            $table->boolean('equipped')->default(false);
            $table->timestamp('earned_at')->useCurrent();
            $table->unique(['user_id', 'badge_code']);
        });

        Schema::create('habnut_user_achievements', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('user_id');
            $table->string('achievement_id', 64);
            $table->unsignedTinyInteger('current_level')->default(0);
            $table->unsignedInteger('current_progress')->default(0);
            $table->timestamp('completed_at')->nullable();
            $table->unique(['user_id', 'achievement_id']);
        });
    }

    private function createModerationTables(): void
    {
        Schema::create('habnut_mutes', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('user_id');
            $table->unsignedBigInteger('muted_by_id');
            $table->string('reason', 255);
            $table->unsignedBigInteger('room_id')->nullable();
            $table->timestamp('expires_at');
            $table->timestamp('lifted_at')->nullable();
            $table->unsignedBigInteger('lifted_by_id')->nullable();
            $table->timestamp('created_at')->useCurrent();
            $table->index(['user_id', 'lifted_at', 'expires_at']);
        });
    }

    private function createHotelActivityTables(): void
    {
        Schema::create('habnut_game_matches', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->string('game_type', 32);
            $table->unsignedBigInteger('room_id')->nullable();
            $table->string('state', 16)->default('waiting');
            $table->unsignedInteger('player_count')->default(0);
            $table->timestamp('started_at')->nullable();
            $table->timestamp('ended_at')->nullable();
        });

        Schema::create('habnut_tournaments', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->string('name', 128);
            $table->string('game_type', 32);
            $table->string('state', 16)->default('scheduled');
            $table->unsignedInteger('entrant_count')->default(0);
            $table->timestamp('starts_at')->nullable();
            $table->timestamp('ends_at')->nullable();
        });

        Schema::create('habnut_garden_seasons', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->string('name', 64);
            $table->boolean('active')->default(false);
            $table->timestamp('starts_at')->nullable();
            $table->timestamp('ends_at')->nullable();
        });

        Schema::create('habnut_garden_goals', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('season_id')->nullable();
            $table->string('name', 128);
            $table->unsignedInteger('target')->default(0);
            $table->unsignedInteger('progress')->default(0);
            $table->timestamp('completed_at')->nullable();
        });

        Schema::create('habnut_garden_plants', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('plot_id');
            $table->string('species', 64);
            $table->unsignedTinyInteger('growth_stage')->default(0);
            $table->timestamp('planted_at')->useCurrent();
            $table->timestamp('watered_at')->nullable();
        });

        Schema::create('habnut_rp_factions', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->string('name', 96);
            $table->string('kind', 32)->default('civilian');
            $table->unsignedInteger('member_count')->default(0);
            $table->timestamps();
        });

        Schema::create('habnut_rp_laws', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->string('code', 32)->unique();
            $table->string('title', 128);
            $table->string('description', 512)->default('');
            $table->unsignedInteger('fine')->default(0);
            $table->unsignedInteger('prison_minutes')->default(0);
        });

        Schema::create('habnut_rp_prison', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('character_id');
            $table->string('reason', 255)->default('');
            $table->timestamp('released_at')->nullable();
            $table->timestamp('created_at')->useCurrent();
        });

        Schema::create('habnut_rp_dispatch_calls', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->string('service', 32)->default('police');
            $table->string('summary', 255)->default('');
            $table->string('state', 16)->default('open');
            $table->unsignedBigInteger('reported_by')->nullable();
            $table->timestamp('created_at')->useCurrent();
            $table->timestamp('closed_at')->nullable();
        });

        Schema::create('habnut_rp_bank_transactions', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('character_id');
            $table->bigInteger('amount');
            $table->string('kind', 32)->default('transfer');
            $table->string('memo', 255)->default('');
            $table->timestamp('created_at')->useCurrent();
        });

        Schema::create('habnut_wired_variables', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->string('scope', 16)->default('room');
            $table->unsignedBigInteger('scope_id')->nullable();
            $table->string('name', 64);
            $table->string('type', 16)->default('number');
            $table->text('value')->nullable();
            $table->timestamp('updated_at')->useCurrent();
        });

        Schema::create('habnut_wired_execution_log', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('room_id')->nullable();
            $table->unsignedBigInteger('item_id')->nullable();
            $table->string('trigger_type', 64)->default('');
            $table->string('outcome', 32)->default('ok');
            $table->text('detail')->nullable();
            $table->timestamp('created_at')->useCurrent();
        });
    }

    public function down(): void
    {
        foreach ([
            'habnut_wired_execution_log', 'habnut_wired_variables',
            'habnut_rp_bank_transactions', 'habnut_rp_dispatch_calls',
            'habnut_rp_prison', 'habnut_rp_laws', 'habnut_rp_factions',
            'habnut_garden_plants', 'habnut_garden_goals', 'habnut_garden_seasons',
            'habnut_tournaments', 'habnut_game_matches', 'habnut_mutes',
            'habnut_user_achievements', 'habnut_user_badges', 'habnut_badges',
            'habnut_friends', 'habnut_forum_subscriptions', 'habnut_forum_reports',
            'habnut_forum_moderators', 'habnut_forum_posts', 'habnut_forum_threads',
            'habnut_forum_categories',
        ] as $table) {
            Schema::dropIfExists($table);
        }
    }
};
