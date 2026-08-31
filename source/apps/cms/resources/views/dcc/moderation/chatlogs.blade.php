@extends('layouts.dcc')
@section('title', 'Chat Logs')
@section('content')
<h1>Chat Log Search</h1>
<form method="GET" style="display:flex;gap:0.75rem;margin-bottom:1.25rem;">
    <input type="text" name="q" value="{{ request('q') }}" placeholder="Search message text (min 3 chars)…" style="max-width:400px;">
    <button type="submit" class="btn btn-primary btn-sm">Search</button>
</form>
@if($logs->isNotEmpty())
<div class="card" style="padding:0;">
<table>
    <thead><tr><th>User</th><th>Message</th><th>Room</th><th>Time</th></tr></thead>
    <tbody>
    @foreach($logs as $l)
    <tr>
        <td>{{ $l->username }}</td>
        <td>{{ $l->message }}</td>
        <td class="text-muted">{{ $l->room_id ?? '—' }}</td>
        <td class="text-muted">{{ \Carbon\Carbon::parse($l->created_at)->format('Y-m-d H:i') }}</td>
    </tr>
    @endforeach
    </tbody>
</table>
</div>
{{ $logs->withQueryString()->links() }}
@elseif(request()->has('q'))
<p class="text-muted">No results for "{{ request('q') }}".</p>
@endif
@endsection
