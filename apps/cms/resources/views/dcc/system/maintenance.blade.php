@extends('layouts.dcc')
@section('title', 'Maintenance')
@section('content')
<h1>Maintenance Mode</h1>
<div class="card" style="max-width:500px;">
    <p style="margin-bottom:1rem;">
        Status: <strong style="color:{{ $active?'var(--danger)':'var(--success)' }}">{{ $active ? 'ACTIVE — Site is in maintenance mode' : 'Inactive' }}</strong>
    </p>
    @if($active)
    <form method="POST" action="{{ route('dcc.system.maintenance.disable') }}">
        @csrf
        <button type="submit" class="btn btn-primary">Disable maintenance mode</button>
    </form>
    @else
    <form method="POST" action="{{ route('dcc.system.maintenance.enable') }}">
        @csrf
        <div class="form-group">
            <label>Message (shown to visitors)</label>
            <input type="text" name="message" value="Habnut is currently undergoing maintenance. We'll be back soon!">
        </div>
        <button type="submit" class="btn btn-danger">Enable maintenance mode</button>
    </form>
    @endif
</div>
@endsection
