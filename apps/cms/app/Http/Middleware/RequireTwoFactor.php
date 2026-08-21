<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class RequireTwoFactor
{
    public function handle(Request $request, Closure $next): Response
    {
        $user = $request->user();
        if ($user && $user->two_factor_enabled && ! session('2fa_verified')) {
            return redirect()->route('2fa.show');
        }

        return $next($request);
    }
}
