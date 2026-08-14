@extends('layouts.dcc')
@section('title','Bank')
@section('content')
<h1>RP — Bank</h1>
<div class="card" style="padding:0;overflow:auto;">
<table><thead><tr>@foreach((array)reset($bank) as $col => $v)<th>{{ $col }}</th>@endforeach</tr></thead>
<tbody>@foreach($bank as $row)<tr>@foreach((array)$row as $v)<td>{{ $v }}</td>@endforeach</tr>@endforeach</tbody></table>
</div>
{{ $bank->links() }}
@endsection
