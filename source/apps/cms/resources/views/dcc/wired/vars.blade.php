@extends('layouts.dcc')
@section('title','Global Variables')
@section('content')
<h1>Global Wired Variables</h1>
<div class="card" style="padding:0;"><table>
<thead><tr><th>Key</th><th>Type</th><th>Value</th><th>Updated</th><th></th></tr></thead>
<tbody>
@foreach($vars as $v)
<tr><td><code>{{ $v->var_key }}</code></td><td>{{ $v->var_type }}</td><td>{{ \Illuminate\Support\Str::limit($v->var_value,60) }}</td>
<td class="text-muted">{{ \Carbon\Carbon::parse($v->updated_at)->format('Y-m-d H:i') }}</td>
<td>
<form method="DELETE" action="{{ route('dcc.wired.vars.delete', $v->var_key) }}">@csrf @method('DELETE')<button class="btn btn-sm btn-danger">Delete</button></form>
</td></tr>
@endforeach
</tbody></table></div>
{{ $vars->links() }}
@endsection
