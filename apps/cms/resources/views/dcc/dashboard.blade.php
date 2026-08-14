@extends('layouts.dcc')
@section('title', 'Dashboard')
@section('content')
<h1>Dashboard</h1>
<div class="stats-row">
    <div class="stat-card">
        <div class="label">Total Users</div>
        <div class="value">{{ number_format($stats['total_users']) }}</div>
    </div>
    <div class="stat-card">
        <div class="label">Online Now</div>
        <div class="value">{{ number_format($stats['online_users']) }}</div>
    </div>
    <div class="stat-card">
        <div class="label">Total Rooms</div>
        <div class="value">{{ number_format($stats['total_rooms']) }}</div>
    </div>
    <div class="stat-card">
        <div class="label">Open Reports</div>
        <div class="value" style="color:{{ $stats['open_reports'] > 0 ? 'var(--danger)' : 'inherit' }}">{{ $stats['open_reports'] }}</div>
    </div>
    <div class="stat-card">
        <div class="label">Active Bans</div>
        <div class="value">{{ $stats['active_bans'] }}</div>
    </div>
</div>

<div class="card">
    <h2>Recent Registrations</h2>
    <table>
        <thead><tr><th>ID</th><th>Username</th><th>Email</th><th>Rank</th><th>Joined</th></tr></thead>
        <tbody>
            @foreach($recent_registrations as $u)
            <tr>
                <td class="text-muted">{{ $u->id }}</td>
                <td><a href="{{ route('dcc.users.show', $u->id) }}">{{ $u->username }}</a></td>
                <td class="text-muted">{{ $u->email }}</td>
                <td><span class="badge badge-warn">{{ $u->rank }}</span></td>
                <td class="text-muted">{{ \Carbon\Carbon::parse($u->created_at)->diffForHumans() }}</td>
            </tr>
            @endforeach
        </tbody>
    </table>
</div>
@endsection
