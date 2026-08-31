@extends('layouts.dcc')
@section('title', $user->username . ' — Audit')
@section('content')
<h1>Audit Log: {{ $user->username }}</h1>
<div class="card" style="padding:0;"><table>
<thead><tr><th>Action</th><th>By</th><th>Meta</th><th>Time</th></tr></thead>
<tbody>
@foreach($logs as $l)
<tr><td><code style="font-size:0.75rem;">{{ $l->action }}</code></td><td>{{ $l->actor_user_id }}</td>
<td class="text-muted">{{ \Illuminate\Support\Str::limit($l->metadata,50) }}</td>
<td class="text-muted">{{ \Carbon\Carbon::parse($l->created_at)->format('Y-m-d H:i') }}</td></tr>
@endforeach
</tbody></table></div>
{{ $logs->links() }}
@endsection
