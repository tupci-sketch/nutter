@extends('layouts.dcc')
@section('title','Staff Commands')
@section('content')
<h1>Command Log</h1>
<div class="card" style="padding:0;"><table>
<thead><tr><th>Staff</th><th>Command</th><th>Time</th></tr></thead>
<tbody>
@foreach($commands as $c)
<tr><td>{{ $c->actor_name }}</td><td><code style="font-size:0.75rem;">{{ $c->action }}</code></td>
<td class="text-muted">{{ \Carbon\Carbon::parse($c->created_at)->format('Y-m-d H:i') }}</td></tr>
@endforeach
</tbody></table></div>
{{ $commands->links() }}
@endsection
