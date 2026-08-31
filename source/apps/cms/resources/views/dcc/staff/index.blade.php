@extends('layouts.dcc')
@section('title','Staff')
@section('content')
<h1>Staff</h1>
<div class="card" style="padding:0;"><table>
<thead><tr><th>Username</th><th>Rank</th><th>Last Login</th></tr></thead>
<tbody>
@foreach($staffByRank as $s)
<tr><td><a href="{{ route('dcc.users.show', $s->id) }}">{{ $s->username }}</a></td>
<td><span class="badge badge-warn">{{ $s->rank }}</span></td>
<td class="text-muted">{{ $s->last_login ? \Carbon\Carbon::parse($s->last_login)->diffForHumans() : 'Never' }}</td></tr>
@endforeach
</tbody></table></div>
@endsection
