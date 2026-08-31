@extends('layouts.dcc')
@section('title','Rooms')
@section('content')
<h1>Rooms</h1>
<form method="GET" style="display:flex;gap:0.5rem;margin-bottom:1rem;">
    <input type="text" name="q" value="{{ request('q') }}" placeholder="Search…" style="max-width:260px;">
    <button type="submit" class="btn btn-sm btn-primary">Search</button>
</form>
<div class="card" style="padding:0;"><table>
<thead><tr><th>ID</th><th>Name</th><th>Owner</th><th>Online</th><th>Featured</th><th></th></tr></thead>
<tbody>
@foreach($rooms as $r)
<tr>
<td>{{ $r->id }}</td><td>{{ $r->name }}</td><td>{{ $r->owner_name }}</td><td>{{ $r->visitors_now }}</td>
<td><span class="badge {{ $r->featured?'badge-green':'badge-red' }}">{{ $r->featured?'Yes':'No' }}</span></td>
<td><a href="{{ route('dcc.rooms.show', $r->id) }}" class="btn btn-sm" style="background:var(--bg3);">View</a></td>
</tr>
@endforeach
</tbody></table></div>
{{ $rooms->withQueryString()->links() }}
@endsection
