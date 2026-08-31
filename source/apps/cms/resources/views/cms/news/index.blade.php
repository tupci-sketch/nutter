@extends('layouts.app')
@section('title', 'News')
@section('content')
<h1>News</h1>
@forelse($articles as $a)
    <div class="card" style="margin-bottom:1rem;">
        <h2><a href="{{ route('news.show', $a->slug) }}" style="color:var(--text);">{{ $a->title }}</a></h2>
        <p class="text-muted" style="font-size:0.8rem;margin:0.3rem 0 0.75rem;">
            {{ $a->published_at->format('F j, Y') }}
            @if($a->author) &bull; {{ $a->author->username }} @endif
        </p>
        <p>{{ $a->excerpt }}</p>
        <a href="{{ route('news.show', $a->slug) }}" style="font-size:0.85rem;display:inline-block;margin-top:0.75rem;">Read more →</a>
    </div>
@empty
    <p class="text-muted">No news articles yet.</p>
@endforelse
{{ $articles->links() }}
@endsection
