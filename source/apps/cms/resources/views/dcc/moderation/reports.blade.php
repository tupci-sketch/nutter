@extends('layouts.dcc')
@section('title', 'Reports')
@section('content')
<h1>Reports</h1>
<form method="GET" style="display:flex;gap:0.5rem;margin-bottom:1rem;">
    <select name="status" style="width:auto;">
        <option value="open" {{ request('status','open')=='open'?'selected':'' }}>Open</option>
        <option value="resolved" {{ request('status')=='resolved'?'selected':'' }}>Resolved</option>
    </select>
    <button type="submit" class="btn btn-sm btn-primary">Filter</button>
</form>
<div class="card" style="padding:0;">
<table>
    <thead><tr><th>ID</th><th>Reporter</th><th>Reported</th><th>Reason</th><th>Status</th><th>Date</th><th></th></tr></thead>
    <tbody>
    @forelse($reports as $r)
    <tr>
        <td>{{ $r->id }}</td>
        <td>{{ $r->reporter_name }}</td>
        <td>{{ $r->reported_name }}</td>
        <td>{{ \Illuminate\Support\Str::limit($r->reason ?? '', 40) }}</td>
        <td><span class="badge {{ $r->status==='open'?'badge-red':'badge-green' }}">{{ $r->status }}</span></td>
        <td class="text-muted">{{ \Carbon\Carbon::parse($r->created_at)->format('Y-m-d') }}</td>
        <td><a href="{{ route('dcc.moderation.report', $r->id) }}" class="btn btn-sm" style="background:var(--bg3);">Review</a></td>
    </tr>
    @empty
        <tr><td colspan="7" class="text-muted" style="padding:1rem;">No reports.</td></tr>
    @endforelse
    </tbody>
</table>
</div>
{{ $reports->withQueryString()->links() }}
@endsection
