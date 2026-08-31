@extends('layouts.dcc')
@section('title','Online Staff')
@section('content')
<h1>Online Staff</h1>
@if(empty($online))
<p class="text-muted">No staff currently online.</p>
@else
<div class="card"><ul>@foreach($online as $s)<li>{{ $s }}</li>@endforeach</ul></div>
@endif
@endsection
