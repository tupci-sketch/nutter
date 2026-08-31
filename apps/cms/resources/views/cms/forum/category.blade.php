@extends('layouts.app')
@section('title', $category->name)

@push('styles')
    @include('cms.forum._style')
@endpush

@section('content')
<div style="max-width:900px;margin:0 auto;display:flex;flex-direction:column;gap:1.2rem;">

    <nav aria-label="Breadcrumb" style="font-size:0.82rem;">
        <a href="{{ route('forum.index') }}">Forums</a>
        <span class="text-muted">&rsaquo; {{ $category->name }}</span>
    </nav>

    <div class="panel">
        <div class="panel-head">
            <span>{{ $category->name }}</span>
            <span class="text-muted" style="font-size:0.8rem;">
                {{ number_format($category->thread_count) }} threads
            </span>
        </div>
        <div class="panel-body">
            <p class="text-muted" style="font-size:0.86rem;margin-bottom:0.9rem;">
                {{ $category->description }}
            </p>

            @forelse($threads as $thread)
                <div class="thread-row">
                    <div class="figure figure-sm" aria-hidden="true">
                        {{ strtoupper(substr($thread->author?->username ?? '?', 0, 1)) }}
                    </div>
                    <div>
                        <a class="title" href="{{ route('forum.thread', $thread->id) }}">
                            @if($thread->pinned)<span class="flag flag-pinned">Pinned</span>@endif
                            @if($thread->locked)<span class="flag flag-locked">Locked</span>@endif
                            {{ $thread->title }}
                        </a>
                        <span class="sub">
                            by {{ $thread->author?->username ?? 'a departed member' }}
                            &middot; {{ $thread->created_at?->diffForHumans() }}
                        </span>
                    </div>
                    <div class="text-muted" style="font-size:0.8rem;">
                        {{ number_format($thread->reply_count) }} replies
                    </div>
                    <div class="text-muted" style="font-size:0.78rem;text-align:right;">
                        @if($thread->lastPoster)
                            {{ $thread->lastPoster->username }}<br>
                        @endif
                        {{ $thread->last_reply_at?->diffForHumans() }}
                    </div>
                </div>
            @empty
                <p class="text-muted">Nobody has started anything here yet.</p>
            @endforelse

            <div style="margin-top:1rem;">{{ $threads->links() }}</div>
        </div>
    </div>

    @auth
        @if($canPost)
            <div class="panel composer">
                <div class="panel-head"><span>Start a thread</span></div>
                <div class="panel-body">
                    <form method="POST" action="{{ route('forum.thread.store', $category->slug) }}">
                        @csrf
                        <div class="form-group">
                            <label for="title">Title</label>
                            <input id="title" name="title" maxlength="255" required
                                   value="{{ old('title') }}">
                            @error('title')<p class="alert alert-error">{{ $message }}</p>@enderror
                        </div>
                        <div class="form-group">
                            <label for="body">Post</label>
                            <textarea id="body" name="body" maxlength="20000" required>{{ old('body') }}</textarea>
                            @error('body')<p class="alert alert-error">{{ $message }}</p>@enderror
                        </div>
                        <button class="btn btn-primary" type="submit">Post thread</button>
                    </form>
                </div>
            </div>
        @elseif($category->locked)
            <p class="text-muted" style="font-size:0.86rem;">This board is archived and takes no new threads.</p>
        @endif
    @else
        <p class="text-muted" style="font-size:0.86rem;">
            <a href="{{ route('login') }}">Log in</a> to join the conversation.
        </p>
    @endauth

</div>
@endsection
