@extends('layouts.dcc')
@section('title', 'Economy')
@section('content')
<h1>Economy Overview</h1>
<div class="stats-row">
    <div class="stat-card"><div class="label">Total Credits</div><div class="value">{{ number_format($totals->total_credits) }}</div></div>
    <div class="stat-card"><div class="label">Total Diamonds</div><div class="value">{{ number_format($totals->total_diamonds) }}</div></div>
    <div class="stat-card"><div class="label">Total Nut Points</div><div class="value">{{ number_format($totals->total_nutpoints) }}</div></div>
    <div class="stat-card"><div class="label">Users</div><div class="value">{{ number_format($totals->users) }}</div></div>
</div>
<div class="card">
    <h2>24h Volume</h2>
    @foreach($volume24h as $currency => $vol)
        <div>{{ ucfirst($currency) }}: <strong>{{ number_format($vol) }}</strong></div>
    @endforeach
</div>
<div style="display:flex;gap:0.75rem;margin-top:1rem;">
    <a href="{{ route('dcc.economy.transactions') }}" class="btn" style="background:var(--bg3);">Transactions</a>
    <a href="{{ route('dcc.economy.stats') }}" class="btn" style="background:var(--bg3);">Stats</a>
</div>
@endsection
