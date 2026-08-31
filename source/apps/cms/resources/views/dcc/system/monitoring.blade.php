@extends('layouts.dcc')
@section('title', 'Monitoring')
@section('content')
<h1>Monitoring</h1>
<p class="text-muted" style="margin-bottom:1rem;">Live metrics and dashboards embedded from Grafana.</p>
<div style="background:var(--bg2);border:1px solid var(--border);border-radius:8px;overflow:hidden;height:700px;">
    <iframe src="{{ $grafanaUrl }}/d/habnut-overview?kiosk=tv" width="100%" height="100%" frameborder="0" style="border:none;"></iframe>
</div>
<p class="text-muted" style="margin-top:0.75rem;font-size:0.8rem;">
    <a href="{{ $grafanaUrl }}" target="_blank">Open Grafana directly →</a>
</p>
@endsection
