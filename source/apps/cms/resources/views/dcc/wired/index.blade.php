@extends('layouts.dcc')
@section('title','Wired')
@section('content')
<h1>Wired 2.0</h1>
<div class="stats-row">
<div class="stat-card"><div class="label">Global Variables</div><div class="value">{{ $globalVarCount }}</div></div>
<div class="stat-card"><div class="label">Executions (24h)</div><div class="value">{{ $execLogCount }}</div></div>
</div>
<a href="{{ route('dcc.wired.vars') }}" class="btn" style="background:var(--bg3);">Global Variables</a>
<a href="{{ route('dcc.wired.execlog') }}" class="btn" style="background:var(--bg3);">Execution Log</a>
@endsection
