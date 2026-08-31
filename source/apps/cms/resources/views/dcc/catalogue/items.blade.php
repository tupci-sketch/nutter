@extends('layouts.dcc')
@section('title','Catalogue Items')
@section('content')
<h1>Catalogue Items</h1>
<form method="GET" style="display:flex;gap:0.5rem;margin-bottom:1rem;">
    <input type="text" name="q" value="{{ request('q') }}" placeholder="Search…" style="max-width:260px;">
    <button type="submit" class="btn btn-sm btn-primary">Search</button>
</form>
<div class="card" style="padding:0;"><table>
<thead><tr><th>Name</th><th>Page</th><th>Credits</th><th>Diamonds</th><th>Qty</th></tr></thead>
<tbody>
@foreach($items as $i)
<tr><td>{{ $i->name }}</td><td>{{ $i->page_name }}</td><td>{{ $i->cost_credits }}</td><td>{{ $i->cost_diamonds }}</td><td>{{ $i->amount }}</td></tr>
@endforeach
</tbody></table></div>
{{ $items->withQueryString()->links() }}
@endsection
