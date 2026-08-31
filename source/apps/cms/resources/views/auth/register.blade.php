@extends('layouts.app')
@section('title', 'Register')
@section('content')
<div style="max-width:420px;margin:4rem auto;">
    <div class="card">
        <div style="text-align:center;margin-bottom:1.5rem;">
            <div style="font-size:2.5rem;">🌰</div>
            <h1 style="margin-bottom:0.25rem;">Create account</h1>
            <p class="text-muted" style="font-size:0.9rem;">Join Habnut today — it's free</p>
        </div>
        <form method="POST" action="{{ route('register') }}">
            @csrf
            <div class="form-group">
                <label>Username</label>
                <input type="text" name="username" value="{{ old('username') }}" required minlength="3" maxlength="30" pattern="[A-Za-z0-9]+" title="Letters and numbers only">
            </div>
            <div class="form-group">
                <label>Email address</label>
                <input type="email" name="email" value="{{ old('email') }}" required>
            </div>
            <div class="form-group">
                <label>Password</label>
                <input type="password" name="password" required minlength="8">
            </div>
            <div class="form-group">
                <label>Confirm password</label>
                <input type="password" name="password_confirmation" required>
            </div>
            <p style="font-size:0.8rem;color:var(--muted);margin-bottom:1rem;">
                By registering, you agree to our <a href="{{ route('terms') }}">Terms of Service</a> and <a href="{{ route('privacy') }}">Privacy Policy</a>.
            </p>
            <button type="submit" class="btn btn-primary" style="width:100%;">Create account</button>
        </form>
        <div style="text-align:center;margin-top:1rem;font-size:0.85rem;color:var(--muted);">
            Already have an account? <a href="{{ route('login') }}">Sign in</a>
        </div>
    </div>
</div>
@endsection
