@extends('layouts.app')
@section('title', 'Two-Factor Authentication')
@section('content')
<div style="max-width:380px;margin:4rem auto;">
    <div class="card" style="text-align:center;">
        <div style="font-size:2rem;margin-bottom:0.5rem;">🔐</div>
        <h1>Verify your identity</h1>
        <p class="text-muted" style="font-size:0.9rem;margin:0.75rem 0 1.5rem;">Enter the 6-digit code from your authenticator app.</p>
        <form method="POST" action="{{ route('2fa.verify') }}">
            @csrf
            <div class="form-group">
                <input type="text" name="code" maxlength="6" pattern="\d{6}" autofocus placeholder="000000"
                       style="text-align:center;font-size:1.5rem;letter-spacing:0.3em;font-family:monospace;">
            </div>
            <button type="submit" class="btn btn-primary" style="width:100%;">Verify</button>
        </form>
    </div>
</div>
@endsection
