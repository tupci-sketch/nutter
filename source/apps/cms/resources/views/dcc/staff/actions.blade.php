@extends('layouts.dcc')
@section('title','Staff Actions')
@section('content')
<h1>Staff Actions</h1>
<div class="card" style="padding:0;"><table>
<thead><tr><th>Staff</th><th>Action</th><th>Target</th><th>Time</th></tr></thead>
<tbody>
@foreach($actions as $a)
<tr><td>{{ $a->actor_name }}</td><td><code style="font-size:0.75rem;">{{ $a->action }}</code></td>
<td class="text-muted">{{ $a->target_type }}:{{ $a->target_id }}</td>
<td class="text-muted">{{ \Carbon\Carbon::parse($a->created_at)->format('Y-m-d H:i') }}</td></tr>
@endforeach
</tbody></table></div>
{{ $actions->links() }}
@endsection
