@extends('layouts.app')
@section('title', $article->title)
@section('content')
<div style="max-width:720px;">
    <p class="text-muted" style="margin-bottom:0.5rem;font-size:0.85rem;">
        <a href="{{ route('news.index') }}">← News</a>
    </p>
    <h1>{{ $article->title }}</h1>
    <p class="text-muted" style="font-size:0.8rem;margin:0.4rem 0 1.5rem;">
        {{ $article->published_at->format('F j, Y') }}
        @if($article->author) &bull; {{ $article->author->username }} @endif
    </p>
    <div style="line-height:1.8;">{{ $article->body }}</div>
</div>
@endsection
