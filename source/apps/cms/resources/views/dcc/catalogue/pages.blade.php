@extends('layouts.dcc')
@section('title','Pages')
@section('content')
<h1>Catalogue Pages</h1>
<div class="card" style="padding:0;"><table>
<thead><tr><th>ID</th><th>Name</th><th>Layout</th><th>Rank</th><th>Order</th><th>Visible</th></tr></thead>
<tbody>
@foreach($pages as $p)
<tr><td>{{ $p->id }}</td><td>{{ $p->name }}</td><td>{{ $p->layout }}</td><td>{{ $p->min_rank }}</td>
<td>{{ $p->order_num }}</td><td><span class="badge {{ $p->visible?'badge-green':'badge-red' }}">{{ $p->visible?'Yes':'No' }}</span></td></tr>
@endforeach
</tbody></table></div>
{{ $pages->links() }}
@endsection
