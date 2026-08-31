@extends('layouts.dcc')
@section('title','Execution Log')
@section('content')
<h1>Wired Execution Log</h1>
<div class="card" style="padding:0;"><table>
<thead><tr><th>Room</th><th>Trigger</th><th>Result</th><th>Duration ms</th><th>Time</th></tr></thead>
<tbody>
@foreach($logs as $l)
<tr><td>{{ $l->room_id }}</td><td>{{ $l->trigger_type }}</td><td><span class="badge {{ $l->success?'badge-green':'badge-red' }}">{{ $l->success?'OK':'Err' }}</span></td>
<td>{{ $l->duration_ms }}</td><td class="text-muted">{{ \Carbon\Carbon::parse($l->created_at)->format('H:i:s') }}</td></tr>
@endforeach
</tbody></table></div>
{{ $logs->links() }}
@endsection
