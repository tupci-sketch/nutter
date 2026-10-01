<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        if (! Schema::hasTable('habnut_audit_logs')) {
            Schema::create('habnut_audit_logs', function (Blueprint $table) {
                $table->id();
                $table->unsignedBigInteger('actor_user_id');
                $table->string('action', 128);
                $table->string('target_type', 64);
                $table->unsignedBigInteger('target_id');
                $table->unsignedBigInteger('target_user_id')->nullable();
                $table->json('metadata')->nullable();
                $table->string('ip_address', 45)->nullable();
                $table->string('room_context')->nullable();
                $table->string('source', 32)->default('dcc');
                $table->timestamp('created_at');

                $table->index(['actor_user_id', 'created_at']);
                $table->index(['target_user_id', 'created_at']);
                $table->index('action');
            });
        }

        if (! Schema::hasTable('habnut_transactions')) {
            Schema::create('habnut_transactions', function (Blueprint $table) {
                $table->id();
                $table->unsignedBigInteger('user_id');
                $table->string('type', 64);
                $table->string('currency', 32)->default('credits');
                $table->integer('amount');
                $table->unsignedInteger('balance_before')->default(0);
                $table->unsignedInteger('balance_after')->default(0);
                $table->string('description')->nullable();
                $table->uuid('idempotency_key')->unique();
                $table->string('transaction_hash', 64);
                $table->timestamp('created_at');

                $table->index(['user_id', 'created_at']);
                $table->index('idempotency_key');
            });
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('habnut_transactions');
        Schema::dropIfExists('habnut_audit_logs');
    }
};
