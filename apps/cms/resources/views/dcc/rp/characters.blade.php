@extends('layouts.dcc')
@section('title','Characters')
@section('content')
<h1>RP Characters</h1>
<form method="GET" style="display:flex;gap:0.5rem;margin-bottom:1rem;">
<input type="text" name="q" value="{{ request('q') }}" placeholder="Search name…" style="max-width:260px;">
<button type="submit" class="btn btn-sm btn-primary">Search</button>
</form>
<div class="card" style="padding:0;"><table>
<thead><tr><th>Name</th><th>User</th><th>Faction</th><th>Health</th><th>Cash</th><th>Prison</th><th></th></tr></thead>
<tbody>
@foreach($characters as $c)
<tr><td>{{ $c->name }} {{ $c->surname }}</td><td>{{ $c->username }}</td><td>{{ $c->faction_id ?? '—' }}</td>
<td>{{ $c->health }}</td><td>{{ number_format($c->cash_balance) }}</td>
<td>{{ $c->prison_expiry ? \Carbon\Carbon::parse($c->prison_expiry)->format('Y-m-d') : '—' }}</td>
<td><a href="{{ route('dcc.rp.character', $c->id) }}" class="btn btn-sm" style="background:var(--bg3);">View</a></td></tr>
@endforeach
</tbody></table></div>
{{ $characters->withQueryString()->links() }}
@endsection
