<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * News articles and help pages.
 *
 * Both are read by public controllers — the landing page lists the latest
 * articles and the help section is linked from every page — but neither table
 * had a migration, so both routes failed on a fresh install.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('news_articles', function (Blueprint $table) {
            $table->id();
            $table->string('title');
            $table->string('slug')->unique();
            $table->string('excerpt', 512)->default('');
            $table->longText('body');
            $table->string('image_url')->nullable();
            $table->foreignId('author_id')->nullable()->constrained('users')->nullOnDelete();
            $table->timestamp('published_at')->nullable()->index();
            $table->timestamps();
        });

        Schema::create('help_articles', function (Blueprint $table) {
            $table->id();
            $table->string('title');
            $table->string('slug')->unique();
            $table->string('category')->default('general')->index();
            $table->longText('body');
            $table->unsignedInteger('order_index')->default(0);
            $table->boolean('published')->default(true)->index();
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('help_articles');
        Schema::dropIfExists('news_articles');
    }
};
