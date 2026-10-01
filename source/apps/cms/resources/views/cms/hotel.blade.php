@extends('layouts.app')
@section('title', 'Hotel')
@section('content')
{{--
    What is left of this page.

    A signed-in player never sees it: the controller mints their ticket and
    sends them straight to the client. This is the two cases that remain — not
    signed in, and signed in but suspended.
--}}
<div style="text-align:center;padding:3rem 0;">
    @isset($banned)
        <h1>You cannot come in at the moment.</h1>
        <p class="text-muted" style="margin:1rem 0;">
            {{ $banned->reason ?: 'Your account has been suspended.' }}
        </p>
        @if($banned->expires_at)
            <p class="text-muted" style="font-size:0.9rem;">
                This lifts on {{ $banned->expires_at->format('j F Y') }}.
            </p>
        @else
            <p class="text-muted" style="font-size:0.9rem;">This does not expire on its own.</p>
        @endif
        <a href="{{ route('help.index') }}" class="btn btn-primary" style="margin-top:1rem;">
            Appeal this
        </a>
    @else
        <h1>You need an account to come in.</h1>
        <p class="text-muted" style="margin:1rem 0;">
            {{ $onlineCount }} {{ Str::plural('person', $onlineCount) }} in the hotel right now.
        </p>
        <a href="{{ route('register') }}" class="btn btn-primary" style="margin-top:1rem;">Create account</a>
        <p style="margin-top:1rem;">
            <a href="{{ route('login') }}" class="text-muted" style="font-size:0.85rem;">
                Already have one? Sign in
            </a>
        </p>
    @endisset
</div>
@endsection
