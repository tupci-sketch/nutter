@extends('layouts.app')
@section('title', 'Home')
@section('content')
<div style="text-align:center;padding:4rem 0 3rem;">
    <div style="font-size:4rem;">🌰</div>
    <h1 style="font-size:2.5rem;color:var(--accent);margin-bottom:0.5rem;">Welcome to Habnut</h1>
    <p style="color:var(--muted);font-size:1.1rem;max-width:520px;margin:0 auto 2rem;">
        Your virtual hotel experience awaits. Decorate rooms, make friends, explore Nutropolis.
    </p>
    @guest
        <a href="{{ route('register') }}" class="btn btn-primary" style="padding:0.75rem 2rem;font-size:1rem;margin-right:0.75rem;">Get started free</a>
        <a href="{{ route('login') }}" class="btn" style="padding:0.75rem 2rem;font-size:1rem;background:var(--bg2);border:1px solid var(--border);">Sign in</a>
    @else
        <a href="{{ route('hotel') }}" class="btn btn-primary" style="padding:0.75rem 2rem;font-size:1rem;">Enter Hotel</a>
    @endguest
</div>

@if($articles->count())
<section>
    <h2 style="margin-bottom:1rem;">Latest News</h2>
    <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:1.25rem;">
        @foreach($articles as $a)
        <div class="card" style="display:flex;flex-direction:column;">
            <h3 style="margin-bottom:0.5rem;"><a href="{{ route('news.show', $a->slug) }}" style="color:var(--text);">{{ $a->title }}</a></h3>
            <p class="text-muted" style="font-size:0.85rem;flex:1;">{{ $a->excerpt }}</p>
            <a href="{{ route('news.show', $a->slug) }}" style="margin-top:1rem;font-size:0.85rem;">Read more →</a>
        </div>
        @endforeach
    </div>
</section>
@endif
@endsection
