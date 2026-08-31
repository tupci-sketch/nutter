@extends('layouts.dcc')
@section('title','Tournaments')
@section('content')
<h1>Tournaments</h1>
<div class="card" style="padding:0;"><table>
<thead><tr><th>ID</th><th>Name</th><th>Game</th><th>Status</th><th>Created</th></tr></thead>
<tbody>
@foreach($tournaments as $t)
<tr><td>{{ $t->id }}</td><td>{{ $t->name }}</td><td>{{ $t->game_type }}</td>
<td><span class="badge {{ $t->status==='active'?'badge-green':'badge-red' }}">{{ $t->status }}</span></td>
<td class="text-muted">{{ \Carbon\Carbon::parse($t->created_at)->format('Y-m-d') }}</td></tr>
@endforeach
</tbody></table></div>
{{ $tournaments->links() }}
@endsection
