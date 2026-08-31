@extends('layouts.dcc')
@section('title','Garden')
@section('content')
<h1>Community Garden</h1>
<div class="stats-row">
<div class="stat-card"><div class="label">Plots</div><div class="value">{{ $plotCount }}</div></div>
<div class="stat-card"><div class="label">Plants</div><div class="value">{{ $plantCount }}</div></div>
<div class="stat-card"><div class="label">Active Season</div><div class="value" style="font-size:1rem;">{{ $activeSeason?$activeSeason->name:'None' }}</div></div>
</div>
<div class="card">
<h2>Start New Season</h2>
<form method="POST" action="{{ route('dcc.garden.season') }}" style="display:flex;gap:0.75rem;align-items:flex-end;margin-top:0.75rem;">
@csrf
<div class="form-group" style="margin:0;flex:1;"><label>Season Name</label><input type="text" name="name" required></div>
<div class="form-group" style="margin:0;flex:1;"><label>Ends At</label><input type="datetime-local" name="ends_at" required></div>
<button type="submit" class="btn btn-primary btn-sm">Start</button>
</form>
</div>
<div style="display:flex;gap:0.75rem;">
<a href="{{ route('dcc.garden.plots') }}" class="btn" style="background:var(--bg3);">Plots</a>
<a href="{{ route('dcc.garden.goals') }}" class="btn" style="background:var(--bg3);">Goals</a>
</div>
@endsection
