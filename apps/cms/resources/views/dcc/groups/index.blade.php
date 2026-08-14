@extends('layouts.dcc')
@section('title','Groups')
@section('content')
<h1>Groups</h1>
<div class="card" style="padding:0;"><table>
<thead><tr><th>ID</th><th>Name</th><th>Owner</th><th>Verified</th><th>Created</th><th></th></tr></thead>
<tbody>
@foreach($groups as $g)
<tr><td>{{ $g->id }}</td><td>{{ $g->name }}</td><td>{{ $g->owner_name }}</td>
<td><span class="badge {{ $g->verified?'badge-green':'badge-red' }}">{{ $g->verified?'Yes':'No' }}</span></td>
<td class="text-muted">{{ \Carbon\Carbon::parse($g->created_at)->format('Y-m-d') }}</td>
<td><a href="{{ route('dcc.groups.show', $g->id) }}" class="btn btn-sm" style="background:var(--bg3);">View</a></td></tr>
@endforeach
</tbody></table></div>
{{ $groups->withQueryString()->links() }}
@endsection
