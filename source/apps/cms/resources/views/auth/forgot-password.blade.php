@extends('layouts.app')
@section('title', 'Reset Password')
@section('content')
<div style="max-width:400px;margin:4rem auto;">
    <div class="card">
        <h1>Reset password</h1>
        <p class="text-muted" style="font-size:0.9rem;margin-bottom:1.25rem;">Enter your email and we'll send you a reset link.</p>
        @if(session('status'))
            <div class="alert alert-success">{{ session('status') }}</div>
        @endif
        <form method="POST" action="{{ route('password.email') }}">
            @csrf
            <div class="form-group">
                <label>Email address</label>
                <input type="email" name="email" value="{{ old('email') }}" required autofocus>
            </div>
            <button type="submit" class="btn btn-primary" style="width:100%;">Send reset link</button>
        </form>
        <div style="text-align:center;margin-top:1rem;font-size:0.85rem;color:var(--muted);">
            <a href="{{ route('login') }}">Back to login</a>
        </div>
    </div>
</div>
@endsection
