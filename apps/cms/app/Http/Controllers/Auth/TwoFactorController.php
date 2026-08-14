<?php

namespace App\Http\Controllers\Auth;

use App\Http\Controllers\Controller;
use Illuminate\Http\Request;
use PragmaRX\Google2FALaravel\Support\Authenticator;

class TwoFactorController extends Controller
{
    public function show()
    {
        if (!auth()->user()->two_factor_enabled) {
            return redirect()->route('home');
        }
        return view('auth.two-factor');
    }

    public function verify(Request $request)
    {
        $request->validate(['code' => ['required', 'string']]);

        $user = $request->user();
        $google2fa = app('pragmarx.google2fa');

        if (!$google2fa->verifyKey($user->two_factor_secret, $request->code)) {
            return back()->withErrors(['code' => 'Invalid authentication code.']);
        }

        session(['2fa_verified' => true]);
        return redirect()->intended(route('home'));
    }

    public function setup(Request $request)
    {
        $user = $request->user();
        $google2fa = app('pragmarx.google2fa');

        if (!$user->two_factor_secret) {
            $secret = $google2fa->generateSecretKey();
            $user->update(['two_factor_secret' => $secret]);
        }

        $qrUrl = $google2fa->getQRCodeUrl('Habnut', $user->email, $user->two_factor_secret);

        return view('auth.two-factor-setup', [
            'secret' => $user->two_factor_secret,
            'qrUrl'  => $qrUrl,
        ]);
    }

    public function enable(Request $request)
    {
        $request->validate(['code' => ['required', 'string']]);

        $user = $request->user();
        $google2fa = app('pragmarx.google2fa');

        if (!$google2fa->verifyKey($user->two_factor_secret, $request->code)) {
            return back()->withErrors(['code' => 'Invalid code. Please try again.']);
        }

        $user->update(['two_factor_enabled' => true]);
        session(['2fa_verified' => true]);

        return redirect()->route('profile.show')->with('success', 'Two-factor authentication enabled.');
    }

    public function disable(Request $request)
    {
        $request->validate(['password' => ['required', 'current_password']]);

        $request->user()->update([
            'two_factor_enabled' => false,
            'two_factor_secret'  => null,
        ]);

        session()->forget('2fa_verified');

        return redirect()->route('profile.show')->with('success', 'Two-factor authentication disabled.');
    }
}
