@extends('layouts.app')
@section('title', 'Help')
@section('content')
<h1>Help Centre</h1>
<div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:1rem;margin-top:1rem;">
    @foreach($articles as $slug => $a)
    <a href="{{ route('help.show', $slug) }}" class="card" style="display:block;color:var(--text);text-decoration:none;">
        <h2 style="color:var(--accent);">{{ $a['title'] }}</h2>
        <p class="text-muted" style="font-size:0.85rem;margin-top:0.4rem;">{{ $a['body'] }}</p>
    </a>
    @endforeach
</div>
@endsection
