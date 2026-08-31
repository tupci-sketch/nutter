@extends('layouts.app')
@section('title', $user->username . "'s Profile")

@push('styles')
<style>
    .profile-head { display: flex; gap: 1.4rem; align-items: flex-start; }
    .profile-head .figure { width: 110px; height: 180px; font-size: 2.4rem; }
    .profile-head .who { flex: 1; min-width: 0; }
    .profile-head h1 { margin-bottom: 0.2rem; }
    .profile-head .motto {
        margin-top: 0.7rem; padding-left: 0.8rem;
        border-left: 2px solid var(--brass-dim); font-style: italic; color: var(--text);
    }
    .profile-meta { margin-top: 0.9rem; font-size: 0.82rem; color: var(--muted); }

    .badge-shelf { display: flex; flex-wrap: wrap; gap: 0.6rem; }
    .badge-shelf .slot {
        width: 52px; height: 52px; display: grid; place-items: center;
        background: var(--bg-deep); border: 1px solid var(--ridge); border-radius: 6px;
    }
    .badge-shelf img { max-width: 100%; max-height: 100%; image-rendering: pixelated; }

    .stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 0.6rem; }
    .stat-grid > div {
        background: var(--bg-deep); border: 1px solid var(--ridge);
        border-radius: 6px; padding: 0.7rem; text-align: center;
    }
    .stat-grid .k {
        font-family: var(--mono); font-size: 0.65rem; letter-spacing: 0.1em;
        text-transform: uppercase; color: var(--muted);
    }
    .stat-grid .v { font-size: 1.25rem; font-weight: 700; color: var(--brass); }

    .listing { display: flex; flex-direction: column; gap: 0.5rem; }
    .listing .row {
        display: flex; align-items: center; gap: 0.7rem;
        padding: 0.5rem 0.6rem; background: var(--bg-deep);
        border: 1px solid var(--ridge); border-radius: 6px;
    }
    .listing .row .n { flex: 1; min-width: 0; }
    .listing .row .n .sub {
        display: block; font-size: 0.76rem; color: var(--muted);
        overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    .listing .group-badge { width: 26px; height: 26px; flex: none; image-rendering: pixelated; }

    @media (max-width: 640px) {
        .profile-head { flex-direction: column; align-items: center; text-align: center; }
        .profile-head .motto { border-left: none; padding-left: 0; }
        .stat-grid { grid-template-columns: repeat(2, 1fr); }
    }
</style>
@endpush

@section('content')
<div style="max-width:760px;margin:0 auto;display:flex;flex-direction:column;gap:1.2rem;">

    <div class="panel">
        <div class="panel-head">
            <span>{{ $user->username }}</span>
            <span class="badge {{ $user->isStaff() ? 'badge-staff' : 'badge-rank' }}">
                {{ $user->rankName() }}
            </span>
        </div>
        <div class="panel-body profile-head">
            <div class="figure">
                @if($user->avatarUrl('l'))
                    <img src="{{ $user->avatarUrl('l') }}" alt="{{ $user->username }}'s figure">
                @else
                    {{ strtoupper(substr($user->username, 0, 1)) }}
                @endif
            </div>
            <div class="who">
                <h1>{{ $user->username }}</h1>
                @if($user->motto)
                    <p class="motto">{{ $user->motto }}</p>
                @endif
                <p class="profile-meta">
                    Member since {{ $user->created_at->format('F Y') }}
                    @if($user->online)
                        &middot; <span style="color:var(--brass);">in the hotel now</span>
                    @endif
                </p>
            </div>
        </div>
    </div>

    <div class="panel">
        <div class="panel-head"><span>Hotel life</span></div>
        <div class="panel-body">
            <div class="stat-grid">
                <div><div class="k">Friends</div><div class="v">{{ number_format($stats['friends']) }}</div></div>
                <div><div class="k">Badges</div><div class="v">{{ number_format($stats['badges']) }}</div></div>
                <div><div class="k">Achievements</div><div class="v">{{ number_format($stats['achievements']) }}</div></div>
                <div><div class="k">Rooms</div><div class="v">{{ number_format($stats['rooms']) }}</div></div>
            </div>
        </div>
    </div>

    @if($badges->isNotEmpty())
        <div class="panel">
            <div class="panel-head"><span>Badges on show</span></div>
            <div class="panel-body">
                <div class="badge-shelf">
                    @foreach($badges as $badge)
                        <div class="slot" title="{{ $badge->name ?? $badge->badge_code }}">
                            @if($user->badgeUrl($badge->badge_code))
                                <img src="{{ $user->badgeUrl($badge->badge_code) }}"
                                     alt="{{ $badge->name ?? $badge->badge_code }}">
                            @else
                                <span class="text-muted" style="font-size:0.7rem;">?</span>
                            @endif
                        </div>
                    @endforeach
                </div>
            </div>
        </div>
    @endif

    @if($groups->isNotEmpty())
        <div class="panel">
            <div class="panel-head"><span>Groups</span></div>
            <div class="panel-body">
                <div class="listing">
                    @foreach($groups as $group)
                        <div class="row">
                            @if(\App\Support\Imager::badge($group->badge))
                                <img class="group-badge"
                                     src="{{ \App\Support\Imager::badge($group->badge) }}"
                                     alt="">
                            @endif
                            <span class="n">
                                {{ $group->name }}
                                <span class="sub">{{ ucfirst($group->rank) }}</span>
                            </span>
                        </div>
                    @endforeach
                </div>
            </div>
        </div>
    @endif

    @if($rooms->isNotEmpty())
        <div class="panel">
            <div class="panel-head"><span>Rooms</span></div>
            <div class="panel-body">
                <div class="listing">
                    @foreach($rooms as $room)
                        <div class="row">
                            <span class="n">
                                {{ $room->name }}
                                @if($room->description)
                                    <span class="sub">{{ $room->description }}</span>
                                @endif
                            </span>
                            <span class="text-muted" style="font-size:0.8rem;">
                                {{ $room->user_count }}/{{ $room->max_users }}
                            </span>
                        </div>
                    @endforeach
                </div>
            </div>
        </div>
    @endif

</div>
@endsection
