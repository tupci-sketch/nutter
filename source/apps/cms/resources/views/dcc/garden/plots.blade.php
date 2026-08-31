@extends('layouts.dcc')
@section('title','Garden Plots')
@section('content')
<h1>Garden Plots</h1>
<div class="card" style="padding:0;"><table>
<thead><tr><th>ID</th><th>User</th><th>Status</th><th>Updated</th></tr></thead>
<tbody>
@foreach($plots as $p)
<tr><td>{{ $p->id }}</td><td>{{ $p->username }}</td><td>{{ $p->status }}</td><td class="text-muted">{{ \Carbon\Carbon::parse($p->updated_at)->format('Y-m-d') }}</td></tr>
@endforeach
</tbody></table></div>
{{ $plots->links() }}
@endsection
