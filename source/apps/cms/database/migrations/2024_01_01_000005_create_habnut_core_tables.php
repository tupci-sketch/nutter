<?php

use App\Support\HotelSchema;
use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        if (! Schema::hasTable('habnut_rooms')) {
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
        }

        if (! Schema::hasTable('habnut_reports')) {
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
        }

        // Mirrors the hotel's own ban table, column for column. A ban applied
        // on the website has to be the same row the hotel checks at the door,
        // or a banned player simply walks back in.
        if (! Schema::hasTable('habnut_bans')) {
            Schema::create('habnut_bans', function (Blueprint $table) {
                $table->bigIncrements('id');
                $table->unsignedBigInteger('user_id');
                $table->unsignedBigInteger('banned_by_id')->nullable();
                $table->string('reason', 512);
                $table->string('ban_type', 16)->default('account');
                $table->string('ip_address', 45)->nullable();
                $table->string('machine_id_hash', 64)->nullable();
                $table->timestamp('created_at')->useCurrent();
                $table->timestamp('expires_at')->nullable();
                $table->timestamp('lifted_at')->nullable();
                $table->unsignedBigInteger('lifted_by_id')->nullable();
                $table->index(['user_id', 'lifted_at']);
            });
        }

        Schema::create('habnut_ban_appeals', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('user_id');
            $table->unsignedBigInteger('ban_id');
            $table->text('message');
            $table->string('status')->default('pending');
            $table->timestamps();
        });

        if (! Schema::hasTable('habnut_groups')) {
            Schema::create('habnut_groups', function (Blueprint $table) {
                $table->bigIncrements('id');
                $table->string('name');
                $table->unsignedBigInteger('owner_id');
                $table->string('badge')->nullable();
                $table->boolean('verified')->default(false);
                $table->timestamps();
            });
        }

        if (! Schema::hasTable('habnut_group_members')) {
            Schema::create('habnut_group_members', function (Blueprint $table) {
                $table->bigIncrements('id');
                $table->unsignedBigInteger('group_id');
                $table->unsignedBigInteger('user_id');
                $table->tinyInteger('rank')->default(0);
                $table->timestamps();
            });
        }

        if (! Schema::hasTable('habnut_catalogue_pages')) {
            Schema::create('habnut_catalogue_pages', function (Blueprint $table) {
                $table->bigIncrements('id');
                $table->string('name');
                $table->string('layout')->default('default');
                $table->tinyInteger('min_rank')->default(1);
                $table->boolean('visible')->default(true);
                $table->integer('order_num')->default(0);
                $table->timestamps();
            });
        }

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

        if (! Schema::hasTable('habnut_chat_logs')) {
            Schema::create('habnut_chat_logs', function (Blueprint $table) {
                $table->bigIncrements('id');
                $table->unsignedBigInteger('user_id');
                $table->unsignedBigInteger('room_id')->nullable();
                $table->text('message');
                $table->string('type')->default('room');
                $table->timestamp('created_at')->useCurrent();
            });
        }

        if (! Schema::hasTable('habnut_leaderboards')) {
            Schema::create('habnut_leaderboards', function (Blueprint $table) {
                $table->bigIncrements('id');
                $table->string('game_type');
                $table->unsignedBigInteger('user_id');
                $table->integer('score')->default(0);
                $table->integer('wins')->default(0);
                $table->integer('losses')->default(0);
                $table->timestamps();
            });
        }

        if (! Schema::hasTable('habnut_garden_plots')) {
            Schema::create('habnut_garden_plots', function (Blueprint $table) {
                $table->bigIncrements('id');
                $table->unsignedBigInteger('user_id')->nullable();
                $table->tinyInteger('plot_x');
                $table->tinyInteger('plot_y');
                $table->string('status')->default('empty');
                $table->timestamps();
            });
        }

        if (! Schema::hasTable('habnut_rp_characters')) {
            Schema::create('habnut_rp_characters', function (Blueprint $table) {
                $table->bigIncrements('id');
                $table->unsignedBigInteger('user_id');
                $table->string('name');
                $table->string('faction')->nullable();
                $table->integer('rp_cash')->default(500);
                $table->boolean('is_jailed')->default(false);
                $table->timestamps();
            });
        }

        if (! Schema::hasTable('habnut_rp_crimes')) {
            Schema::create('habnut_rp_crimes', function (Blueprint $table) {
                $table->bigIncrements('id');
                $table->unsignedBigInteger('character_id');
                $table->string('type');
                $table->string('status')->default('open');
                $table->timestamps();
            });
        }

        if (! Schema::hasTable('habnut_rp_court_cases')) {
            Schema::create('habnut_rp_court_cases', function (Blueprint $table) {
                $table->bigIncrements('id');
                $table->unsignedBigInteger('defendant_id');
                $table->string('charge');
                $table->string('verdict')->nullable();
                $table->string('status')->default('pending');
                $table->timestamps();
            });
        }

        if (! Schema::hasTable('habnut_word_filter')) {
            Schema::create('habnut_word_filter', function (Blueprint $table) {
                $table->bigIncrements('id');
                $table->string('word')->unique();
                $table->string('severity')->default('low');
                $table->timestamps();
            });
        }

        if (! Schema::hasTable('habnut_feature_flags')) {
            Schema::create('habnut_feature_flags', function (Blueprint $table) {
                $table->bigIncrements('id');
                $table->string('key')->unique();
                $table->boolean('enabled')->default(false);
                $table->text('description')->nullable();
                $table->timestamps();
            });
        }

        if (! Schema::hasTable('habnut_system_settings')) {
            Schema::create('habnut_system_settings', function (Blueprint $table) {
                $table->bigIncrements('id');
                $table->string('key')->unique();
                $table->text('value')->nullable();
                $table->timestamps();
            });
        }
    }

    public function down(): void
    {
        HotelSchema::dropIfOurs('habnut_system_settings');
        HotelSchema::dropIfOurs('habnut_feature_flags');
        HotelSchema::dropIfOurs('habnut_word_filter');
        HotelSchema::dropIfOurs('habnut_rp_court_cases');
        HotelSchema::dropIfOurs('habnut_rp_crimes');
        HotelSchema::dropIfOurs('habnut_rp_characters');
        HotelSchema::dropIfOurs('habnut_garden_plots');
        HotelSchema::dropIfOurs('habnut_leaderboards');
        HotelSchema::dropIfOurs('habnut_chat_logs');
        HotelSchema::dropIfOurs('habnut_catalogue_items');
        HotelSchema::dropIfOurs('habnut_catalogue_pages');
        HotelSchema::dropIfOurs('habnut_group_members');
        HotelSchema::dropIfOurs('habnut_groups');
        HotelSchema::dropIfOurs('habnut_ban_appeals');
        HotelSchema::dropIfOurs('habnut_bans');
        HotelSchema::dropIfOurs('habnut_reports');
        HotelSchema::dropIfOurs('habnut_rooms');
    }
};
