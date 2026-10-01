<?php

namespace App\Http\Controllers\Auth;

use App\Http\Controllers\Controller;
use App\Services\SessionTicketService;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
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

        if ($user->two_fa_enabled) {
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

    /**
     * A ticket for the hotel, for a caller that wants one rather than a redirect.
     *
     * The page that opens the hotel does not use this — the controller sends
     * the player through with a ticket already made — but the client asks for
     * one when its own has expired, rather than throwing the player back to
     * the website.
     */
    public function ticket(Request $request, SessionTicketService $tickets)
    {
        $user = $request->user();

        if ($user->activeBan()) {
            return response()->json(['error' => 'Your account has been suspended.'], 403);
        }

        $world = $tickets->normaliseWorld($request->input('world'));

        return response()->json([
            'ticket' => $tickets->issue($user, $world),
            'world' => $world,
            'expiresIn' => $tickets->ttlSeconds(),
        ]);
    }
}
