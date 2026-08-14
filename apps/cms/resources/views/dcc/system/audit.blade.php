@extends('layouts.dcc')
@section('title', 'Audit Log')
@section('content')
<h1>Audit Log</h1>
<form method="GET" style="display:flex;gap:0.5rem;margin-bottom:1rem;">
    <input type="text" name="action" value="{{ request('action') }}" placeholder="Filter by action…" style="max-width:260px;">
    <button type="submit" class="btn btn-sm btn-primary">Filter</button>
</form>
<div class="card" style="padding:0;">
<table>
    <thead><tr><th>Actor</th><th>Action</th><th>Target</th><th>IP</th><th>Time</th></tr></thead>
    <tbody>
    @foreach($logs as $l)
    <tr>
        <td>{{ $l->actor_name }}</td>
        <td><code style="font-size:0.78rem;">{{ $l->action }}</code></td>
        <td class="text-muted">{{ $l->target_type }}:{{ $l->target_id }}</td>
        <td class="text-muted">{{ $l->ip_address }}</td>
        <td class="text-muted">{{ \Carbon\Carbon::parse($l->created_at)->format('Y-m-d H:i') }}</td>
    </tr>
    @endforeach
    </tbody>
</table>
</div>
{{ $logs->withQueryString()->links() }}
@endsection
