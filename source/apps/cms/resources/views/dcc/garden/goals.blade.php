@extends('layouts.dcc')
@section('title','Garden Goals')
@section('content')
<h1>Garden Goals</h1>
<div class="card" style="padding:0;"><table>
<thead><tr><th>ID</th><th>Name</th><th>Target</th><th>Current</th><th>Completed</th></tr></thead>
<tbody>
@foreach($goals as $g)
<tr><td>{{ $g->id }}</td><td>{{ $g->name }}</td><td>{{ $g->target_value }}</td><td>{{ $g->current_value }}</td>
<td><span class="badge {{ $g->completed_at?'badge-green':'badge-warn' }}">{{ $g->completed_at?'Yes':'No' }}</span></td></tr>
@endforeach
</tbody></table></div>
{{ $goals->links() }}
@endsection
