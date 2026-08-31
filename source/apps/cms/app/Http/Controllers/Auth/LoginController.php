<?php

namespace App\Http\Controllers\Auth;

use App\Http\Controllers\Controller;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

class LoginController extends Controller
{
    public function show()
    {
        return view('auth.login');
    }

    public function store(Request $request)
    {
        $request->validate([
            'email' => ['required', 'string'],
            'password' => ['required', 'string'],
        ]);

        if (! Auth::attempt(['email' => $request->email, 'password' => $request->password], $request->boolean('remember'))) {
            throw ValidationException::withMessages([
                'email' => __('auth.failed'),
            ]);
        }

        $user = Auth::user();

        if ($user->activeBan()) {
            Auth::logout();
            throw ValidationException::withMessages([
                'email' => 'Your account has been suspended.',
            ]);
        }

        $user->update([
            'last_login' => now(),
            'last_ip' => $request->ip(),
        ]);

        $request->session()->regenerate();

        if ($user->two_factor_enabled) {
            return redirect()->route('2fa.show');
        }

        return redirect()->intended(route('home'));
    }

    public function destroy(Request $request)
    {
        Auth::logout();
        $request->session()->invalidate();
        $request->session()->regenerateToken();

        return redirect()->route('login');
    }

    public function ticket(Request $request)
    {
        $user = $request->user();
        $ticket = 'HNT-'.Str::upper(Str::random(32));
        cache()->put("ticket:{$ticket}", $user->id, now()->addMinutes(5));

        return response()->json(['ticket' => $ticket]);
    }
}
