@extends('layouts.dcc')
@section('title', $character->name . ' ' . $character->surname)
@section('content')
<h1>{{ $character->name }} {{ $character->surname }}</h1>
<div style="display:grid;grid-template-columns:1fr 1fr;gap:1.25rem;">
<div class="card"><h2>Details</h2><table>
<tr><td class="text-muted">User</td><td>{{ $character->username }}</td></tr>
<tr><td class="text-muted">Health</td><td>{{ $character->health }}</td></tr>
<tr><td class="text-muted">Cash</td><td>{{ number_format($character->cash_balance) }}</td></tr>
<tr><td class="text-muted">Bank</td><td>{{ number_format($character->bank_balance) }}</td></tr>
<tr><td class="text-muted">Prison Until</td><td>{{ $character->prison_expiry ?? 'Free' }}</td></tr>
</table>
@if($character->prison_expiry)
<form method="POST" action="{{ route('dcc.rp.pardon', $character->id) }}" style="margin-top:1rem;">@csrf<button class="btn btn-primary btn-sm">Pardon</button></form>
@endif
</div>
<div class="card"><h2>Crimes ({{ count($crimes) }})</h2><table>
<thead><tr><th>Type</th><th>Date</th></tr></thead><tbody>
@foreach($crimes as $c)<tr><td>{{ $c->type }}</td><td class="text-muted">{{ \Carbon\Carbon::parse($c->created_at)->format('Y-m-d') }}</td></tr>@endforeach
</tbody></table></div>
</div>
@endsection
