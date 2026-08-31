@extends('layouts.dcc')
@section('title', $group->name)
@section('content')
<h1>{{ $group->name }}</h1>
<div class="card">
<p class="text-muted">{{ $group->description }}</p>
<div style="display:flex;gap:0.75rem;margin-top:1rem;">
    @if(!$group->verified)
    <form method="PUT" action="{{ route('dcc.groups.verify', $group->id) }}">@csrf @method('PUT')<button class="btn btn-primary btn-sm">Verify</button></form>
    @endif
    <form method="POST" action="{{ route('dcc.groups.destroy', $group->id) }}">@csrf @method('DELETE')<button class="btn btn-danger btn-sm">Delete</button></form>
</div>
<h2 style="margin-top:1.5rem;">Members ({{ $members->count() }})</h2>
<table><thead><tr><th>Username</th><th>Role</th></tr></thead><tbody>
@foreach($members as $m)<tr><td>{{ $m->username }}</td><td>{{ $m->rank ?? 'member' }}</td></tr>@endforeach
</tbody></table>
</div>
@endsection
