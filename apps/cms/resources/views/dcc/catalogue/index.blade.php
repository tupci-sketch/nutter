@extends('layouts.dcc')
@section('title','Catalogue')
@section('content')
<h1>Catalogue</h1>
<div class="stats-row">
    <div class="stat-card"><div class="label">Pages</div><div class="value">{{ $pageCount }}</div></div>
    <div class="stat-card"><div class="label">Items</div><div class="value">{{ $itemCount }}</div></div>
    <div class="stat-card"><div class="label">Limited Sold</div><div class="value">{{ $limitedSold }}</div></div>
</div>
<a href="{{ route('dcc.catalogue.pages') }}" class="btn" style="background:var(--bg3);">Pages</a>
<a href="{{ route('dcc.catalogue.items') }}" class="btn" style="background:var(--bg3);">Items</a>
<a href="{{ route('dcc.catalogue.limited') }}" class="btn" style="background:var(--bg3);">Limited</a>
@endsection
