@extends('layouts.dcc')
@section('title','Court')
@section('content')
<h1>RP — Court</h1>
<div class="card" style="padding:0;overflow:auto;">
<table><thead><tr>@foreach((array)reset($court) as $col => $v)<th>{{ $col }}</th>@endforeach</tr></thead>
<tbody>@foreach($court as $row)<tr>@foreach((array)$row as $v)<td>{{ $v }}</td>@endforeach</tr>@endforeach</tbody></table>
</div>
{{ $court->links() }}
@endsection
