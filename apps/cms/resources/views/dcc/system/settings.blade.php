@extends('layouts.dcc')
@section('title','Settings')
@section('content')
<h1>System Settings</h1>
<div class="card" style="padding:0;"><table>
<thead><tr><th>Key</th><th>Value</th></tr></thead>
<tbody>
@foreach($settings as $s)
<tr><td><code>{{ $s->setting_key }}</code></td><td>{{ $s->setting_value }}</td></tr>
@endforeach
</tbody></table></div>
{{ $settings->links() }}
@endsection
