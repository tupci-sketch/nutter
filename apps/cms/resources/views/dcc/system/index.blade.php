@extends('layouts.dcc')
@section('title', 'System')
@section('content')
<h1>System</h1>
<div class="stats-row">
    <div class="stat-card">
        <div class="label">Feature Flags</div>
        <div class="value">{{ $flagCount }}</div>
    </div>
    <div class="stat-card">
        <div class="label">Settings</div>
        <div class="value">{{ $settingCount }}</div>
    </div>
    <div class="stat-card">
        <div class="label">Maintenance Mode</div>
        <div class="value" style="font-size:1rem;color:{{ $maintenance?'var(--danger)':'var(--success)' }}">{{ $maintenance ? 'ACTIVE' : 'Off' }}</div>
    </div>
</div>
<div style="display:flex;gap:0.75rem;flex-wrap:wrap;">
    <a href="{{ route('dcc.system.settings') }}" class="btn" style="background:var(--bg3);">Settings</a>
    <a href="{{ route('dcc.system.flags') }}" class="btn" style="background:var(--bg3);">Feature Flags</a>
    <a href="{{ route('dcc.system.monitoring') }}" class="btn" style="background:var(--bg3);">Monitoring</a>
    <a href="{{ route('dcc.system.audit') }}" class="btn" style="background:var(--bg3);">Audit Log</a>
    <a href="{{ route('dcc.system.maintenance') }}" class="btn {{ $maintenance?'btn-danger':'' }}" style="{{ $maintenance?'':'background:var(--bg3);' }}">Maintenance</a>
</div>
@endsection
