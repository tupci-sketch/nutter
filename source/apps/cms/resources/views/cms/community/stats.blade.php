@extends('layouts.app')
@section('title','Hotel statistics')

@push('styles')
<style>
    .figures { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 0.6rem; }
    .figures > div {
        background: var(--bg-deep); border: 1px solid var(--ridge);
        border-radius: 6px; padding: 0.8rem; text-align: center;
    }
    .figures .k {
        font-family: var(--mono); font-size: 0.64rem; letter-spacing: 0.1em;
        text-transform: uppercase; color: var(--muted);
    }
    .figures .v { font-size: 1.5rem; font-weight: 700; color: var(--brass); }
    .rank-row {
        display: flex; align-items: center; gap: 0.7rem;
        padding: 0.45rem 0.6rem; background: var(--bg-deep);
        border: 1px solid var(--ridge); border-radius: 6px;
    }
    .rank-row + .rank-row { margin-top: 0.4rem; }
    .rank-row .pos {
        width: 24px; text-align: center; font-family: var(--mono);
        color: var(--muted); font-size: 0.8rem;
    }
    .rank-row .n { flex: 1; min-width: 0; }
    .rank-row .v { font-weight: 700; color: var(--brass); font-variant-numeric: tabular-nums; }
    .rank-row .figure { width: 26px; height: 40px; font-size: 0.75rem; }
    .board-columns { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 1rem; }
</style>
@endpush

@section('content')
<div style="max-width:900px;margin:0 auto;display:flex;flex-direction:column;gap:1.2rem;">

    <div class="panel">
        <div class="panel-head"><span>The hotel today</span></div>
        <div class="panel-body">
            <div class="figures">
                <div><div class="k">Players</div><div class="v">{{ number_format($stats['players']) }}</div></div>
                <div><div class="k">Online</div><div class="v">{{ number_format($stats['online']) }}</div></div>
                <div><div class="k">Rooms</div><div class="v">{{ number_format($stats['rooms']) }}</div></div>
                <div><div class="k">Groups</div><div class="v">{{ number_format($stats['groups']) }}</div></div>
                <div><div class="k">Joined today</div><div class="v">{{ number_format($stats['joinedToday']) }}</div></div>
            </div>
        </div>
    </div>

    @if($topPlayers->isNotEmpty())
        <div class="panel">
            <div class="panel-head"><span>Most decorated</span></div>
            <div class="panel-body">
                @foreach($topPlayers as $i => $player)
                    <div class="rank-row">
                        <span class="pos">{{ $i + 1 }}</span>
                        <div class="figure">
                            @if($player->avatarUrl('s'))
                                <img src="{{ $player->avatarUrl('s') }}" alt="">
                            @else
                                {{ strtoupper(substr($player->username, 0, 1)) }}
                            @endif
                        </div>
                        <span class="n">
                            <a href="{{ route('profile.view', $player->username) }}">{{ $player->username }}</a>
                        </span>
                        <span class="v">{{ number_format($player->achievement_score) }}</span>
                    </div>
                @endforeach
            </div>
        </div>
    @endif

    @if($leaderboards->isNotEmpty())
        <div class="panel">
            <div class="panel-head"><span>Games</span></div>
            <div class="panel-body">
                <div class="board-columns">
                    @foreach($leaderboards as $game => $rows)
                        <div>
                            <h3 style="font-size:0.9rem;margin-bottom:0.5rem;text-transform:capitalize;">
                                {{ str_replace('_', ' ', $game) }}
                            </h3>
                            @foreach($rows as $i => $row)
                                <div class="rank-row">
                                    <span class="pos">{{ $i + 1 }}</span>
                                    <span class="n">{{ $row->username }}</span>
                                    <span class="v">{{ number_format($row->wins) }}</span>
                                </div>
                            @endforeach
                        </div>
                    @endforeach
                </div>
            </div>
        </div>
    @endif

    @if($busiestRooms->isNotEmpty())
        <div class="panel">
            <div class="panel-head"><span>Busiest rooms</span></div>
            <div class="panel-body">
                @foreach($busiestRooms as $i => $room)
                    <div class="rank-row">
                        <span class="pos">{{ $i + 1 }}</span>
                        <span class="n">
                            {{ $room->name }}
                            @if($room->owner)
                                <span class="text-muted" style="font-size:0.78rem;">by {{ $room->owner }}</span>
                            @endif
                        </span>
                        <span class="v">{{ $room->user_count }}/{{ $room->max_users }}</span>
                    </div>
                @endforeach
            </div>
        </div>
    @endif

</div>
@endsection
