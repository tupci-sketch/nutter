<?php

namespace App\Providers;

use Illuminate\Support\ServiceProvider;
use App\Services\AuditService;

class AppServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        $this->app->singleton(AuditService::class);
    }

    public function boot(): void
    {
        //
    }
}
