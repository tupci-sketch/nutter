@extends('layouts.app')
@section('title', $thread->title)

@push('styles')
    @include('cms.forum._style')
@endpush

@section('content')
<div style="max-width:900px;margin:0 auto;display:flex;flex-direction:column;gap:1.2rem;">

    <nav aria-label="Breadcrumb" style="font-size:0.82rem;">
        <a href="{{ route('forum.index') }}">Forums</a>
        @if($thread->category)
            <span class="text-muted">&rsaquo;</span>
            <a href="{{ route('forum.category', $thread->category->slug) }}">{{ $thread->category->name }}</a>
        @elseif($thread->group_id)
            <span class="text-muted">&rsaquo;</span>
            <a href="{{ route('forum.group', $thread->group_id) }}">Group forum</a>
        @endif
        <span class="text-muted">&rsaquo; {{ $thread->title }}</span>
    </nav>

    <div class="panel">
        <div class="panel-head">
            <span>
                @if($thread->pinned)<span class="flag flag-pinned">Pinned</span>@endif
                @if($thread->locked)<span class="flag flag-locked">Locked</span>@endif
                @if($thread->hidden)<span class="flag flag-hidden">Hidden</span>@endif
                {{ $thread->title }}
            </span>
            <span class="text-muted" style="font-size:0.8rem;">
                {{ number_format($thread->views) }} views
            </span>
        </div>

        <div>
            @foreach($posts as $post)
                <article class="post {{ $post->hidden ? 'is-hidden' : '' }}" id="post-{{ $post->id }}">
                    <div class="who">
                        <div class="figure">
                            @if($post->author?->avatarUrl())
                                <img src="{{ $post->author->avatarUrl() }}" alt="">
                            @else
                                {{ strtoupper(substr($post->author?->username ?? '?', 0, 1)) }}
                            @endif
                        </div>
                        <div class="name">
                            @if($post->author)
                                <a href="{{ route('profile.view', $post->author->username) }}">
                                    {{ $post->author->username }}
                                </a>
                            @else
                                A departed member
                            @endif
                        </div>
                        @if($post->author)
                            <div class="sub">{{ $post->author->rankName() }}</div>
                            <div class="sub">Joined {{ $post->author->created_at->format('M Y') }}</div>
                        @endif
                    </div>

                    <div class="said">
                        <div class="stamp">
                            <span>{{ $post->created_at->format('j M Y, H:i') }}</span>
                            @if($post->wasEdited())
                                <span>edited {{ $post->edited_at->diffForHumans() }}</span>
                            @endif
                        </div>

                        @if($post->hidden)
                            <p class="hidden-note">
                                Hidden by a moderator{{ $post->hidden_reason ? ': '.$post->hidden_reason : '.' }}
                            </p>
                        @endif

                        <div class="body">{{ $post->body }}</div>

                        @auth
                            <div class="tools">
                                @if($canModerate)
                                    <form class="inline-form" method="POST"
                                          action="{{ route('forum.post.moderate', $post->id) }}">
                                        @csrf
                                        <input type="hidden" name="action"
                                               value="{{ $post->hidden ? 'restore' : 'hide' }}">
                                        @unless($post->hidden)
                                            <input type="hidden" name="reason" value="Removed by a moderator">
                                        @endunless
                                        <button class="btn btn-sm" type="submit">
                                            {{ $post->hidden ? 'Restore' : 'Hide' }}
                                        </button>
                                    </form>
                                @endif

                                @if(! $post->hidden && $post->author_id !== auth()->id())
                                    <details>
                                        <summary class="btn btn-sm" style="list-style:none;">Report</summary>
                                        <form method="POST" action="{{ route('forum.post.report', $post->id) }}"
                                              style="margin-top:0.5rem;max-width:340px;">
                                            @csrf
                                            <input name="reason" maxlength="512" required
                                                   placeholder="What is wrong with this post?">
                                            <button class="btn btn-sm" type="submit"
                                                    style="margin-top:0.4rem;">Send report</button>
                                        </form>
                                    </details>
                                @endif
                            </div>
                        @endauth
                    </div>
                </article>
            @endforeach
        </div>

        <div class="panel-body">{{ $posts->links() }}</div>
    </div>

    @if($canModerate)
        <div class="panel">
            <div class="panel-head"><span>Moderation</span></div>
            <div class="panel-body" style="display:flex;flex-wrap:wrap;gap:0.4rem;">
                @foreach([
                    $thread->pinned ? 'unpin' : 'pin',
                    $thread->locked ? 'unlock' : 'lock',
                    $thread->hidden ? 'unhide' : 'hide',
                ] as $action)
                    <form class="inline-form" method="POST"
                          action="{{ route('forum.thread.moderate', $thread->id) }}">
                        @csrf
                        <input type="hidden" name="action" value="{{ $action }}">
                        <button class="btn btn-sm" type="submit">{{ ucfirst($action) }} thread</button>
                    </form>
                @endforeach
            </div>
        </div>
    @endif

    @auth
        @if($canReply)
            <div class="panel composer">
                <div class="panel-head"><span>Reply</span></div>
                <div class="panel-body">
                    <form method="POST" action="{{ route('forum.post.store', $thread->id) }}">
                        @csrf
                        <div class="form-group">
                            <label for="body" class="sr-only">Your reply</label>
                            <textarea id="body" name="body" maxlength="20000" required
                                      placeholder="Say something">{{ old('body') }}</textarea>
                            @error('body')<p class="alert alert-error">{{ $message }}</p>@enderror
                        </div>
                        <button class="btn btn-primary" type="submit">Post reply</button>
                    </form>
                </div>
            </div>
        @elseif($thread->locked)
            <p class="text-muted" style="font-size:0.86rem;">This thread is locked.</p>
        @endif
    @else
        <p class="text-muted" style="font-size:0.86rem;">
            <a href="{{ route('login') }}">Log in</a> to reply.
        </p>
    @endauth

</div>
@endsection
