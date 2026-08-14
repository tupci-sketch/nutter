@extends('layouts.dcc')
@section('title','Laws')
@section('content')
<h1>RP — Laws</h1>
<div class="card" style="padding:0;overflow:auto;">
<table><thead><tr>@foreach((array)reset($laws) as $col => $v)<th>{{ $col }}</th>@endforeach</tr></thead>
<tbody>@foreach($laws as $row)<tr>@foreach((array)$row as $v)<td>{{ $v }}</td>@endforeach</tr>@endforeach</tbody></table>
</div>
{{ $laws->links() }}
@endsection
