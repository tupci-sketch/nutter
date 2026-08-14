<?php

use Illuminate\Support\Facades\Route;

Route::middleware('auth:sanctum')->group(function () {
    Route::get('/user', fn(\Illuminate\Http\Request $r) => $r->user());
    Route::post('/ticket', [\App\Http\Controllers\Auth\LoginController::class, 'ticket'])->name('api.ticket');
});
