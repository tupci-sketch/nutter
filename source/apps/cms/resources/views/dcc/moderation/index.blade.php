@extends('layouts.dcc')
@section('title', 'Moderation')
@section('content')
<h1>Trust & Safety</h1>
<div class="stats-row">
    <div class="stat-card">
        <div class="label">Open Reports</div>
        <div class="value" style="color:{{ $openReports>0?'var(--danger)':'inherit' }}">{{ $openReports }}</div>
    </div>
    <div class="stat-card">
        <div class="label">Active Bans</div>
        <div class="value">{{ $activeBans }}</div>
    </div>
    <div class="stat-card">
        <div class="label">Pending Appeals</div>
        <div class="value" style="color:{{ $openAppeals>0?'var(--warn)':'inherit' }}">{{ $openAppeals }}</div>
    </div>
</div>
<div style="display:flex;gap:0.75rem;flex-wrap:wrap;">
    <a href="{{ route('dcc.moderation.reports') }}" class="btn btn-primary">View Reports</a>
    <a href="{{ route('dcc.moderation.bans') }}" class="btn" style="background:var(--bg3);">Manage Bans</a>
    <a href="{{ route('dcc.moderation.appeals') }}" class="btn" style="background:var(--bg3);">Appeals</a>
    <a href="{{ route('dcc.moderation.wordfilter') }}" class="btn" style="background:var(--bg3);">Word Filter</a>
    <a href="{{ route('dcc.moderation.chatlogs') }}" class="btn" style="background:var(--bg3);">Chat Logs</a>
</div>
@endsection
