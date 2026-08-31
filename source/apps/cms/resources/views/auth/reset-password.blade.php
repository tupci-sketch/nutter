@extends('layouts.app')
@section('title', 'New Password')
@section('content')
<div style="max-width:400px;margin:4rem auto;">
    <div class="card">
        <h1>Set new password</h1>
        <form method="POST" action="{{ route('password.update') }}">
            @csrf
            <input type="hidden" name="token" value="{{ $token }}">
            <div class="form-group">
                <label>Email address</label>
                <input type="email" name="email" value="{{ old('email', $email) }}" required>
            </div>
            <div class="form-group">
                <label>New password</label>
                <input type="password" name="password" required minlength="8">
            </div>
            <div class="form-group">
                <label>Confirm password</label>
                <input type="password" name="password_confirmation" required>
            </div>
            <button type="submit" class="btn btn-primary" style="width:100%;">Reset password</button>
        </form>
    </div>
</div>
@endsection
