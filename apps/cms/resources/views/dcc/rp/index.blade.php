@extends('layouts.dcc')
@section('title','Nutropolis RP')
@section('content')
<h1>Nutropolis</h1>
<div class="stats-row">
<div class="stat-card"><div class="label">Characters</div><div class="value">{{ $charCount }}</div></div>
<div class="stat-card"><div class="label">Imprisoned</div><div class="value" style="color:var(--danger)">{{ $imprisoned }}</div></div>
<div class="stat-card"><div class="label">Active Dispatch</div><div class="value" style="color:var(--warn)">{{ $activeDispatch }}</div></div>
</div>
<div style="display:flex;gap:0.75rem;flex-wrap:wrap;">
<a href="{{ route('dcc.rp.characters') }}" class="btn" style="background:var(--bg3);">Characters</a>
<a href="{{ route('dcc.rp.factions') }}" class="btn" style="background:var(--bg3);">Factions</a>
<a href="{{ route('dcc.rp.court') }}" class="btn" style="background:var(--bg3);">Court</a>
<a href="{{ route('dcc.rp.bank') }}" class="btn" style="background:var(--bg3);">Bank</a>
<a href="{{ route('dcc.rp.laws') }}" class="btn" style="background:var(--bg3);">Laws</a>
</div>
@endsection
