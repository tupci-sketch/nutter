@extends('layouts.app')
@section('title', 'My Profile')
@section('content')
<h1>My Profile</h1>
<div style="display:grid;grid-template-columns:1fr 1fr;gap:1.5rem;max-width:800px;">
    <div class="card">
        <h2>Account Details</h2>
        <form method="POST" action="{{ route('profile.update') }}" style="margin-top:1rem;">
            @csrf @method('PUT')
            <div class="form-group">
                <label>Username</label>
                <input type="text" value="{{ $user->username }}" disabled style="opacity:0.5;">
            </div>
            <div class="form-group">
                <label>Email</label>
                <input type="email" name="email" value="{{ $user->email }}" required>
            </div>
            <div class="form-group">
                <label>New password (leave blank to keep)</label>
                <input type="password" name="password">
            </div>
            <div class="form-group">
                <label>Confirm new password</label>
                <input type="password" name="password_confirmation">
            </div>
            <button type="submit" class="btn btn-primary">Save changes</button>
        </form>
    </div>
    <div>
        <div class="card">
            <h2>Motto</h2>
            <form method="POST" action="{{ route('profile.motto') }}" style="margin-top:0.75rem;">
                @csrf
                <div class="form-group">
                    <input type="text" name="motto" value="{{ $user->motto }}" maxlength="255" placeholder="Write something about yourself…">
                </div>
                <button type="submit" class="btn btn-primary btn-sm">Update</button>
            </form>
        </div>
        <div class="card">
            <h2>Currencies</h2>
            <table>
                <tr><td>Credits</td><td><strong>{{ number_format($user->credits) }}</strong></td></tr>
                <tr><td>Diamonds</td><td><strong>{{ number_format($user->diamonds) }}</strong></td></tr>
                <tr><td>Nut Points</td><td><strong>{{ number_format($user->nut_points) }}</strong></td></tr>
            </table>
        </div>
        <div class="card">
            <h2>Security</h2>
            <p style="font-size:0.85rem;color:var(--muted);margin-bottom:0.75rem;">
                Two-factor authentication: <strong style="color:{{ $user->two_factor_enabled ? 'var(--success)' : 'var(--danger)' }}">
                {{ $user->two_factor_enabled ? 'Enabled' : 'Disabled' }}</strong>
            </p>
            @if($user->two_factor_enabled)
                <form method="POST" action="{{ route('2fa.disable') }}">
                    @csrf
                    <div class="form-group">
                        <input type="password" name="password" placeholder="Current password" required>
                    </div>
                    <button type="submit" class="btn btn-danger btn-sm">Disable 2FA</button>
                </form>
            @else
                <a href="{{ route('2fa.setup') }}" class="btn btn-primary btn-sm">Set up 2FA</a>
            @endif
        </div>
    </div>
</div>
@endsection
