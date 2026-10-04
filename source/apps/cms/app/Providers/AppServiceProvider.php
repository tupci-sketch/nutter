<?php

namespace App\Providers;

use App\Services\AuditService;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\ServiceProvider;

class AppServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        $this->app->singleton(AuditService::class);
    }

    public function boot(): void
    {
        // A rollback, reset, refresh, fresh or wipe on a live hotel would take
        // the players' accounts and rooms with it, so in production Laravel
        // refuses them outright rather than asking once and accepting --force.
        DB::prohibitDestructiveCommands($this->app->isProduction());
    }
}
