@extends('layouts.app')
@section('title', 'Login')
@section('content')
<div style="max-width:400px;margin:4rem auto;">
    <div class="card">
        <div style="text-align:center;margin-bottom:1.5rem;">
            <div style="font-size:2.5rem;">🌰</div>
            <h1 style="margin-bottom:0.25rem;">Welcome back</h1>
            <p class="text-muted" style="font-size:0.9rem;">Sign in to your Habnut account</p>
        </div>
        @if(session('status'))
            <div class="alert alert-success">{{ session('status') }}</div>
        @endif
        <form method="POST" action="{{ route('login') }}">
            @csrf
            <div class="form-group">
                <label>Email address</label>
                <input type="email" name="email" value="{{ old('email') }}" required autofocus>
            </div>
            <div class="form-group">
                <label>Password</label>
                <input type="password" name="password" required>
            </div>
            <div class="form-group" style="display:flex;align-items:center;gap:0.5rem;">
                <input type="checkbox" name="remember" id="remember" style="width:auto;">
                <label for="remember" style="margin:0;color:var(--text);">Remember me</label>
            </div>
            <button type="submit" class="btn btn-primary" style="width:100%;margin-top:0.5rem;">Sign in</button>
        </form>
        <div style="text-align:center;margin-top:1rem;font-size:0.85rem;color:var(--muted);">
            <a href="{{ route('password.request') }}">Forgot password?</a>
            &bull;
            <a href="{{ route('register') }}">Create account</a>
        </div>
    </div>
</div>
@endsection
