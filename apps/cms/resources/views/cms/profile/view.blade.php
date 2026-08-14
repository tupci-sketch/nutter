@extends('layouts.app')
@section('title', $user->username . "'s Profile")
@section('content')
<div style="max-width:600px;">
    <div class="card" style="display:flex;gap:1.5rem;align-items:flex-start;">
        <div style="width:80px;height:80px;background:var(--bg3);border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:2rem;flex-shrink:0;">
            🌰
        </div>
        <div style="flex:1;">
            <h1 style="margin-bottom:0.25rem;">{{ $user->username }}</h1>
            <p class="text-muted" style="font-size:0.85rem;">Member since {{ $user->created_at->format('F Y') }}</p>
            @if($user->motto)
                <p style="margin-top:0.75rem;font-style:italic;">"{{ $user->motto }}"</p>
            @endif
        </div>
    </div>
</div>
@endsection
