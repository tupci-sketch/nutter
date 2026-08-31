@extends('layouts.dcc')
@section('title', $user->username . ' — Transactions')
@section('content')
<h1>Transactions: {{ $user->username }}</h1>
<div class="card" style="padding:0;"><table>
<thead><tr><th>Type</th><th>Currency</th><th>Amount</th><th>Description</th><th>Date</th></tr></thead>
<tbody>
@foreach($transactions as $t)
<tr><td><code style="font-size:0.75rem;">{{ $t->type }}</code></td><td>{{ $t->currency }}</td>
<td>{{ number_format($t->amount) }}</td>
<td class="text-muted">{{ \Illuminate\Support\Str::limit($t->description,50) }}</td>
<td class="text-muted">{{ \Carbon\Carbon::parse($t->created_at)->format('Y-m-d H:i') }}</td></tr>
@endforeach
</tbody></table></div>
{{ $transactions->links() }}
@endsection
