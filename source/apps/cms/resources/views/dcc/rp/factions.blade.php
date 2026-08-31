@extends('layouts.dcc')
@section('title','Factions')
@section('content')
<h1>RP — Factions</h1>
<div class="card" style="padding:0;overflow:auto;">
<table><thead><tr>@foreach((array)reset($factions) as $col => $v)<th>{{ $col }}</th>@endforeach</tr></thead>
<tbody>@foreach($factions as $row)<tr>@foreach((array)$row as $v)<td>{{ $v }}</td>@endforeach</tr>@endforeach</tbody></table>
</div>
{{ $factions->links() }}
@endsection
