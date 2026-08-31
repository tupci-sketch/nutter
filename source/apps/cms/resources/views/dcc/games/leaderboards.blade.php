@extends('layouts.dcc')
@section('title','Leaderboards')
@section('content')
<h1>Leaderboards</h1>
<div class="card" style="padding:0;"><table>
<thead><tr><th>Rank</th><th>User</th><th>Game</th><th>Score</th></tr></thead>
<tbody>
@foreach($leaderboards as $i => $l)
<tr><td>{{ $i+1 }}</td><td>{{ $l->username }}</td><td>{{ $l->game_type }}</td><td>{{ number_format($l->score) }}</td></tr>
@endforeach
</tbody></table></div>
{{ $leaderboards->links() }}
@endsection
