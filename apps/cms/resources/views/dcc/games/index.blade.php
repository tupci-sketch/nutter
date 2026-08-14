@extends('layouts.dcc')
@section('title','Games')
@section('content')
<h1>Games</h1>
<div class="stats-row">
<div class="stat-card"><div class="label">Total Matches</div><div class="value">{{ $matchCount }}</div></div>
<div class="stat-card"><div class="label">Tournaments</div><div class="value">{{ $tournamentCount }}</div></div>
<div class="stat-card"><div class="label">Active Tournaments</div><div class="value">{{ $activeTournaments }}</div></div>
</div>
<a href="{{ route('dcc.games.matches') }}" class="btn" style="background:var(--bg3);">Matches</a>
<a href="{{ route('dcc.games.leaderboards') }}" class="btn" style="background:var(--bg3);">Leaderboards</a>
<a href="{{ route('dcc.games.tournaments') }}" class="btn" style="background:var(--bg3);">Tournaments</a>
@endsection
