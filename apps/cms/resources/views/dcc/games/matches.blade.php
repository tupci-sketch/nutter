@extends('layouts.dcc')
@section('title','Matches')
@section('content')
<h1>Game Matches</h1>
<div class="card" style="padding:0;"><table>
<thead><tr><th>ID</th><th>Type</th><th>Room</th><th>State</th><th>Started</th></tr></thead>
<tbody>
@foreach($matches as $m)
<tr><td>{{ $m->id }}</td><td>{{ $m->game_type }}</td><td>{{ $m->room_id }}</td><td>{{ $m->state }}</td>
<td class="text-muted">{{ \Carbon\Carbon::parse($m->started_at)->format('Y-m-d H:i') }}</td></tr>
@endforeach
</tbody></table></div>
{{ $matches->links() }}
@endsection
