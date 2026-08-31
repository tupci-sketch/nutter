@extends('layouts.dcc')
@section('title','Limited Editions')
@section('content')
<h1>Limited Editions</h1>
<div class="card" style="padding:0;"><table>
<thead><tr><th>Name</th><th>Stack</th><th>Sold</th><th>Remaining</th></tr></thead>
<tbody>
@foreach($items as $i)
<tr><td>{{ $i->name }}</td><td>{{ $i->limited_stack }}</td><td>{{ $i->limited_sells }}</td><td>{{ $i->limited_stack-$i->limited_sells }}</td></tr>
@endforeach
</tbody></table></div>
{{ $items->links() }}
@endsection
