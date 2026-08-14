@extends('layouts.app')
@section('title', 'Hotel')
@section('content')
<div style="text-align:center;padding:3rem 0;">
    @auth
        <h1>Entering the hotel…</h1>
        <p class="text-muted" style="margin:1rem 0;">The hotel client will launch in a moment.</p>
        <script>
            (function() {
                fetch('/api/ticket', { method: 'POST', headers: { 'X-CSRF-TOKEN': document.querySelector('meta[name=csrf-token]').content } })
                    .then(r => r.json())
                    .then(d => { window.location.href = '{{ env("CLIENT_URL", "http://localhost:5173") }}?ticket=' + d.ticket; })
                    .catch(() => { document.getElementById('err').style.display = 'block'; });
            })();
        </script>
        <div id="err" style="display:none;" class="alert alert-error">Could not connect to hotel. Please try again.</div>
        <a href="{{ route('home') }}" class="text-muted" style="font-size:0.85rem;">← Back to home</a>
    @else
        <h1>You need an account to enter.</h1>
        <a href="{{ route('register') }}" class="btn btn-primary" style="margin-top:1rem;">Create account</a>
    @endauth
</div>
@endsection
