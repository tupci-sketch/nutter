@extends('layouts.app')
@section('title', 'Forums')

@push('styles')
    @include('cms.forum._style')
@endpush

@section('content')
<div style="max-width:900px;margin:0 auto;display:flex;flex-direction:column;gap:1.2rem;">

    <div class="panel">
        <div class="panel-head">
            <span>Forums</span>
            <span class="text-muted" style="font-size:0.8rem;">{{ $categories->count() }} boards</span>
        </div>
        <div class="panel-body">
            @if($categories->isEmpty())
                <p class="text-muted">There are no boards you can see yet.</p>
            @else
                <div class="board-list">
                    @foreach($categories as $category)
                        <a class="board" href="{{ route('forum.category', $category->slug) }}"
                           style="color:inherit;">
                            <div>
                                <div class="name">
                                    {{ $category->name }}
                                    @if($category->locked)
                                        <span class="flag flag-locked">Archived</span>
                                    @endif
                                    @if($category->min_read_rank > 0)
                                        <span class="flag flag-pinned">Staff</span>
                                    @endif
                                </div>
                                <div class="desc">{{ $category->description }}</div>
                            </div>
                            <div class="count">
                                <div class="v">{{ number_format($category->thread_count) }}</div>
                                <div class="k">Threads</div>
                            </div>
                            <div class="latest">
                                @if($category->last_post_at)
                                    Last post {{ $category->last_post_at->diffForHumans() }}
                                @else
                                    No posts yet
                                @endif
                            </div>
                        </a>
                    @endforeach
                </div>
            @endif
        </div>
    </div>

    @if($latest->isNotEmpty())
        <div class="panel">
            <div class="panel-head"><span>Latest talk</span></div>
            <div class="panel-body">
                @foreach($latest as $thread)
                    <div class="thread-row">
                        <div class="figure figure-sm" aria-hidden="true">
                            {{ strtoupper(substr($thread->author?->username ?? '?', 0, 1)) }}
                        </div>
                        <div>
                            <a class="title" href="{{ route('forum.thread', $thread->id) }}">{{ $thread->title }}</a>
                            <span class="sub">
                                in {{ $thread->category?->name }}
                                @if($thread->lastPoster)
                                    &middot; last by {{ $thread->lastPoster->username }}
                                @endif
                                &middot; {{ $thread->last_reply_at?->diffForHumans() }}
                            </span>
                        </div>
                        <div class="text-muted" style="font-size:0.8rem;">
                            {{ number_format($thread->reply_count) }} replies
                        </div>
                        <div></div>
                    </div>
                @endforeach
            </div>
        </div>
    @endif

</div>
@endsection
