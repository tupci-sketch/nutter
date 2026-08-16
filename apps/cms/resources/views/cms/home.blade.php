@extends('layouts.app')
@section('title', Auth::check() ? 'Me' : 'Welcome')

@push('styles')
<style>
    .me-grid { display: grid; grid-template-columns: 320px 1fr; gap: 1.25rem; align-items: start; }
    @media (max-width: 860px) { .me-grid { grid-template-columns: 1fr; } }

    .me-card { display: flex; gap: 1rem; }
    .me-card .meta { min-width: 0; flex: 1; }
    .me-card .username { font-size: 1.25rem; font-weight: 700; letter-spacing: -0.01em; }
    .me-card .motto {
        color: var(--muted); font-size: 0.88rem; margin-top: 0.15rem;
        overflow-wrap: anywhere;
    }
    .me-stats { display: grid; grid-template-columns: repeat(2, 1fr); gap: 1px;
                background: var(--ridge); border-top: 1px solid var(--ridge); }
    .me-stats div { background: var(--panel); padding: 0.7rem 1rem; }
    .me-stats .k { font-family: var(--mono); font-size: 0.64rem; letter-spacing: 0.12em;
                   text-transform: uppercase; color: var(--muted); }
    .me-stats .v { font-family: var(--mono); font-size: 1.05rem; font-variant-numeric: tabular-nums; }

    /* The way in is the loudest thing on the page. */
    .enter {
        background: linear-gradient(135deg, rgba(240,160,64,0.10) 0%, transparent 60%), var(--panel);
        border: 1px solid var(--brass-dim);
        border-radius: 8px; padding: 1.5rem;
        display: flex; align-items: center; gap: 1.25rem; flex-wrap: wrap;
    }
    .enter .copy { flex: 1; min-width: 220px; }
    .enter h2 { font-size: 1.35rem; margin-bottom: 0.3rem; }
    .enter p { color: var(--muted); font-size: 0.9rem; }
    .enter .go { font-size: 1rem; padding: 0.8rem 2rem; }

    .presence { display: flex; align-items: center; gap: 0.45rem; font-size: 0.85rem; }

    .roster { display: flex; flex-direction: column; gap: 0.6rem; }
    .roster-row { display: flex; align-items: center; gap: 0.65rem; }
    .roster-row .n { flex: 1; min-width: 0; font-size: 0.88rem; overflow-wrap: anywhere; }

    .news-list { display: flex; flex-direction: column; gap: 0.9rem; }
    .news-item { display: flex; flex-direction: column; gap: 0.25rem; }
    .news-item h3 { font-size: 0.98rem; }
    .news-item .when { font-family: var(--mono); font-size: 0.7rem; color: var(--muted); }
    .news-item p { color: var(--muted); font-size: 0.86rem; }

    .rank-list { display: flex; flex-direction: column; gap: 0.5rem; }
    .rank-row { display: flex; align-items: center; gap: 0.6rem; font-size: 0.88rem; }
    .rank-row .pos { font-family: var(--mono); color: var(--muted); width: 1.4rem; flex: none; }
    .rank-row .score { font-family: var(--mono); font-variant-numeric: tabular-nums; color: var(--brass); }
</style>
@endpush

@section('content')

@guest
    {{-- Signed out: introduce the hotel and get out of the way. --}}
    <div class="panel" style="margin-bottom:1.25rem;">
        <div class="panel-body" style="text-align:center;padding:3rem 1.5rem;">
            <div style="font-size:3rem;">{{ config('habnut.brand_acorn') }}</div>
            <h1 style="margin:0.5rem 0 0.4rem;">{{ config('habnut.hotel_name') }}</h1>
            <p class="text-muted" style="max-width:44ch;margin:0 auto 1.5rem;">
                Build rooms, trade furniture, and make a name for yourself —
                or cross into {{ config('habnut.nutropolis_name') }} and live another life entirely.
            </p>
            <div style="display:flex;gap:0.6rem;justify-content:center;flex-wrap:wrap;">
                <a href="{{ route('register') }}" class="btn btn-primary" style="padding:0.7rem 1.8rem;">Create an account</a>
                <a href="{{ route('login') }}" class="btn" style="padding:0.7rem 1.8rem;">Sign in</a>
            </div>
            <p class="presence" style="justify-content:center;margin-top:1.5rem;">
                <i class="pip {{ $onlineCount > 0 ? 'pip-on' : 'pip-off' }}"></i>
                <span class="text-muted">{{ number_format($onlineCount) }} online right now</span>
            </p>
        </div>
    </div>
@endguest

<div class="me-grid">

    {{-- ── left column ─────────────────────────────────────────────────── --}}
    <div class="stack">
        @auth
            @php($me = auth()->user())
            <div class="panel">
                <div class="panel-head">
                    <span>Me</span>
                    <span class="badge badge-rank">{{ $me->rankName() }}</span>
                </div>
                <div class="panel-body me-card">
                    <div class="figure">
                        @if($me->avatarUrl())
                            <img src="{{ $me->avatarUrl() }}" alt="">
                        @else
                            {{ strtoupper(substr($me->username, 0, 1)) }}
                        @endif
                    </div>
                    <div class="meta">
                        <div class="username">{{ $me->username }}</div>
                        <div class="motto">{{ $me->motto ?: 'No motto set' }}</div>
                        <a href="{{ route('profile.show') }}" class="btn btn-sm" style="margin-top:0.7rem;">Edit profile</a>
                    </div>
                </div>
                <div class="me-stats">
                    <div><div class="k">Credits</div><div class="v">{{ number_format($me->credits) }}</div></div>
                    <div><div class="k">Diamonds</div><div class="v">{{ number_format($me->diamonds) }}</div></div>
                    <div><div class="k">Nut Points</div><div class="v">{{ number_format($me->nut_points) }}</div></div>
                    <div><div class="k">Score</div><div class="v">{{ number_format($me->achievement_score) }}</div></div>
                </div>
            </div>
        @endauth

        <div class="panel">
            <div class="panel-head">
                <span>Who's in</span>
                <span class="presence">
                    <i class="pip {{ $onlineCount > 0 ? 'pip-on' : 'pip-off' }}"></i>
                    {{ number_format($onlineCount) }}
                </span>
            </div>
            <div class="panel-body">
                @if($staffOnline->isEmpty())
                    <p class="text-muted" style="font-size:0.86rem;">
                        No staff are online at the moment. Check the
                        <a href="{{ route('help.index') }}">help pages</a> if you need a hand.
                    </p>
                @else
                    <div class="roster">
                        @foreach($staffOnline as $staff)
                            <div class="roster-row">
                                <div class="figure figure-sm">
                                    @if($staff->avatarUrl('s'))
                                        <img src="{{ $staff->avatarUrl('s') }}" alt="">
                                    @else
                                        {{ strtoupper(substr($staff->username, 0, 1)) }}
                                    @endif
                                </div>
                                <span class="n">
                                    <a href="{{ route('profile.view', $staff->username) }}">{{ $staff->username }}</a>
                                </span>
                                <span class="badge badge-staff">{{ $staff->rankName() }}</span>
                            </div>
                        @endforeach
                    </div>
                @endif
            </div>
        </div>

        @if($topPlayers->isNotEmpty())
        <div class="panel">
            <div class="panel-head"><span>Top players</span></div>
            <div class="panel-body">
                <div class="rank-list">
                    @foreach($topPlayers as $i => $player)
                        <div class="rank-row">
                            <span class="pos">{{ $i + 1 }}</span>
                            <span style="flex:1;min-width:0;overflow-wrap:anywhere;">
                                <a href="{{ route('profile.view', $player->username) }}">{{ $player->username }}</a>
                            </span>
                            <span class="score">{{ number_format($player->achievement_score) }}</span>
                        </div>
                    @endforeach
                </div>
            </div>
        </div>
        @endif
    </div>

    {{-- ── right column ────────────────────────────────────────────────── --}}
    <div class="stack">
        @auth
            <div class="enter">
                <div class="copy">
                    <h2>{{ config('habnut.hotel_name') }}</h2>
                    <p>{{ number_format($onlineCount) }} {{ Str::plural('player', $onlineCount) }} in the hotel right now.</p>
                </div>
                <a href="{{ route('hotel') }}" class="btn btn-primary go">Enter Hotel</a>
            </div>
        @endauth

        <div class="panel">
            <div class="panel-head">
                <span>Latest news</span>
                <a href="{{ route('news.index') }}" style="font-size:0.72rem;">All news</a>
            </div>
            <div class="panel-body">
                @if($articles->isEmpty())
                    <p class="text-muted" style="font-size:0.86rem;">No news has been posted yet.</p>
                @else
                    <div class="news-list">
                        @foreach($articles as $article)
                            <article class="news-item">
                                <span class="when">
                                    {{ optional($article->published_at)->format('d M Y') }}
                                </span>
                                <h3><a href="{{ route('news.show', $article->slug) }}">{{ $article->title }}</a></h3>
                                <p>{{ $article->excerpt }}</p>
                            </article>
                        @endforeach
                    </div>
                @endif
            </div>
        </div>
    </div>

</div>
@endsection
