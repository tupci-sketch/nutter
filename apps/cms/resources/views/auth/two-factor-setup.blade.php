@extends('layouts.app')
@section('title', 'Set Up Two-Factor Auth')
@section('content')
<div style="max-width:440px;margin:3rem auto;">
    <div class="card">
        <h1>Set up two-factor authentication</h1>
        <ol style="margin:1rem 0 1.5rem;padding-left:1.25rem;color:var(--text);font-size:0.9rem;line-height:1.8;">
            <li>Install an authenticator app (Google Authenticator, Authy, etc.)</li>
            <li>Scan the QR code below or enter the secret manually</li>
            <li>Enter the 6-digit code to confirm</li>
        </ol>
        <div style="text-align:center;margin-bottom:1.25rem;">
            <img src="https://chart.googleapis.com/chart?chs=200x200&chld=M|0&cht=qr&chl={{ urlencode($qrUrl) }}"
                 alt="QR Code" style="border:4px solid #fff;border-radius:8px;">
            <div style="margin-top:0.75rem;font-family:monospace;letter-spacing:0.1em;font-size:0.85rem;color:var(--muted);">
                {{ chunk_split($secret, 4, ' ') }}
            </div>
        </div>
        <form method="POST" action="{{ route('2fa.enable') }}">
            @csrf
            <div class="form-group">
                <label>Confirmation code</label>
                <input type="text" name="code" maxlength="6" pattern="\d{6}" required autofocus placeholder="000000"
                       style="font-family:monospace;letter-spacing:0.2em;text-align:center;">
            </div>
            <button type="submit" class="btn btn-primary" style="width:100%;">Enable 2FA</button>
        </form>
    </div>
</div>
@endsection
