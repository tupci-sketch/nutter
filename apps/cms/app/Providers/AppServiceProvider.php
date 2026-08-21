<?php

namespace App\Providers;

use App\Services\AuditService;
use Illuminate\Support\ServiceProvider;

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
