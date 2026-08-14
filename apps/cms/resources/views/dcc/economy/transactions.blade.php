@extends('layouts.dcc')
@section('title', 'Transactions')
@section('content')
<h1>Transactions</h1>
<form method="GET" style="display:flex;gap:0.5rem;margin-bottom:1rem;">
    <select name="currency" style="width:auto;"><option value="">All currencies</option>
        @foreach(['credits','diamonds','nut_points','seasonal_currency'] as $c)
        <option value="{{ $c }}" {{ request('currency')===$c?'selected':'' }}>{{ ucfirst(str_replace('_',' ',$c)) }}</option>
        @endforeach
    </select>
    <button type="submit" class="btn btn-sm btn-primary">Filter</button>
</form>
<div class="card" style="padding:0;">
<table>
    <thead><tr><th>User</th><th>Type</th><th>Currency</th><th>Amount</th><th>Description</th><th>Date</th></tr></thead>
    <tbody>
    @foreach($transactions as $t)
    <tr>
        <td>{{ $t->username }}</td>
        <td><code style="font-size:0.75rem;">{{ $t->type }}</code></td>
        <td>{{ $t->currency }}</td>
        <td>{{ number_format($t->amount) }}</td>
        <td class="text-muted">{{ \Illuminate\Support\Str::limit($t->description ?? '', 40) }}</td>
        <td class="text-muted">{{ \Carbon\Carbon::parse($t->created_at)->format('Y-m-d H:i') }}</td>
    </tr>
    @endforeach
    </tbody>
</table>
</div>
{{ $transactions->withQueryString()->links() }}
@endsection
