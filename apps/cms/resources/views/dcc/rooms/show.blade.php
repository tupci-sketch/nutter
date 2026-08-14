@extends('layouts.dcc')
@section('title', $room->name)
@section('content')
<h1>{{ $room->name }}</h1>
<div class="card"><table>
<tr><td class="text-muted">Owner</td><td>{{ $room->owner_name }}</td></tr>
<tr><td class="text-muted">State</td><td>{{ $room->state }}</td></tr>
<tr><td class="text-muted">Capacity</td><td>{{ $room->users_max }}</td></tr>
<tr><td class="text-muted">Online now</td><td>{{ $room->visitors_now }}</td></tr>
<tr><td class="text-muted">Featured</td><td>{{ $room->featured ? 'Yes' : 'No' }}</td></tr>
</table>
<div style="display:flex;gap:0.75rem;margin-top:1rem;">
    <form method="POST" action="{{ route('dcc.rooms.feature', $room->id) }}">@csrf<button class="btn btn-sm" style="background:var(--bg3);">{{ $room->featured ? 'Unfeature' : 'Feature' }}</button></form>
    <form method="POST" action="{{ route('dcc.rooms.destroy', $room->id) }}">@csrf @method('DELETE')<button class="btn btn-sm btn-danger">Delete</button></form>
</div>
</div>
@endsection
