<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void
    {
        Schema::create('habnut_rooms', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->string('name');
            $table->unsignedBigInteger('owner_id')->nullable();
            $table->string('model_id')->default('model_a');
            $table->tinyInteger('max_users')->default(25);
            $table->boolean('is_public')->default(false);
            $table->boolean('is_featured')->default(false);
            $table->unsignedInteger('visits')->default(0);
            $table->timestamps();
        });

        Schema::create('habnut_reports', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('reporter_id');
            $table->unsignedBigInteger('target_user_id')->nullable();
            $table->string('type')->default('user');
            $table->text('reason');
            $table->string('status')->default('open');
            $table->unsignedBigInteger('assigned_to')->nullable();
            $table->timestamps();
        });

        Schema::create('habnut_bans', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('user_id');
            $table->string('reason');
            $table->string('banned_by_username');
            $table->boolean('active')->default(true);
            $table->timestamp('expires_at')->nullable();
            $table->timestamps();
        });

        Schema::create('habnut_ban_appeals', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('user_id');
            $table->unsignedBigInteger('ban_id');
            $table->text('message');
            $table->string('status')->default('pending');
            $table->timestamps();
        });

        Schema::create('habnut_groups', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->string('name');
            $table->unsignedBigInteger('owner_id');
            $table->string('badge')->nullable();
            $table->boolean('verified')->default(false);
            $table->timestamps();
        });

        Schema::create('habnut_group_members', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('group_id');
            $table->unsignedBigInteger('user_id');
            $table->tinyInteger('rank')->default(0);
            $table->timestamps();
        });

        Schema::create('habnut_catalogue_pages', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->string('name');
            $table->string('layout')->default('default');
            $table->tinyInteger('min_rank')->default(1);
            $table->boolean('visible')->default(true);
            $table->integer('order_num')->default(0);
            $table->timestamps();
        });

        Schema::create('habnut_catalogue_items', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('page_id');
            $table->string('item_name');
            $table->string('base_item')->nullable();
            $table->unsignedInteger('credits_cost')->default(0);
            $table->unsignedInteger('diamonds_cost')->default(0);
            $table->boolean('is_limited')->default(false);
            $table->timestamps();
        });

        Schema::create('habnut_chat_logs', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('user_id');
            $table->unsignedBigInteger('room_id')->nullable();
            $table->text('message');
            $table->string('type')->default('room');
            $table->timestamp('created_at')->useCurrent();
        });

        Schema::create('habnut_leaderboards', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->string('game_type');
            $table->unsignedBigInteger('user_id');
            $table->integer('score')->default(0);
            $table->integer('wins')->default(0);
            $table->integer('losses')->default(0);
            $table->timestamps();
        });

        Schema::create('habnut_garden_plots', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('user_id')->nullable();
            $table->tinyInteger('plot_x');
            $table->tinyInteger('plot_y');
            $table->string('status')->default('empty');
            $table->timestamps();
        });

        Schema::create('habnut_rp_characters', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('user_id');
            $table->string('name');
            $table->string('faction')->nullable();
            $table->integer('rp_cash')->default(500);
            $table->boolean('is_jailed')->default(false);
            $table->timestamps();
        });

        Schema::create('habnut_rp_crimes', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('character_id');
            $table->string('type');
            $table->string('status')->default('open');
            $table->timestamps();
        });

        Schema::create('habnut_rp_court_cases', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('defendant_id');
            $table->string('charge');
            $table->string('verdict')->nullable();
            $table->string('status')->default('pending');
            $table->timestamps();
        });

        Schema::create('habnut_word_filter', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->string('word')->unique();
            $table->string('severity')->default('low');
            $table->timestamps();
        });

        Schema::create('habnut_feature_flags', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->string('key')->unique();
            $table->boolean('enabled')->default(false);
            $table->text('description')->nullable();
            $table->timestamps();
        });

        Schema::create('habnut_system_settings', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->string('key')->unique();
            $table->text('value')->nullable();
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('habnut_system_settings');
        Schema::dropIfExists('habnut_feature_flags');
        Schema::dropIfExists('habnut_word_filter');
        Schema::dropIfExists('habnut_rp_court_cases');
        Schema::dropIfExists('habnut_rp_crimes');
        Schema::dropIfExists('habnut_rp_characters');
        Schema::dropIfExists('habnut_garden_plots');
        Schema::dropIfExists('habnut_leaderboards');
        Schema::dropIfExists('habnut_chat_logs');
        Schema::dropIfExists('habnut_catalogue_items');
        Schema::dropIfExists('habnut_catalogue_pages');
        Schema::dropIfExists('habnut_group_members');
        Schema::dropIfExists('habnut_groups');
        Schema::dropIfExists('habnut_ban_appeals');
        Schema::dropIfExists('habnut_bans');
        Schema::dropIfExists('habnut_reports');
        Schema::dropIfExists('habnut_rooms');
    }
};
