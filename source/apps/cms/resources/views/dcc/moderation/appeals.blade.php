@extends('layouts.dcc')
@section('title', 'Appeals')
@section('content')
<h1>Ban Appeals</h1>
<div class="card" style="padding:0;">
<table>
    <thead><tr><th>User</th><th>Statement</th><th>Status</th><th>Date</th><th></th></tr></thead>
    <tbody>
    @forelse($appeals as $a)
    <tr>
        <td>{{ $a->username }}</td>
        <td>{{ \Illuminate\Support\Str::limit($a->statement ?? '', 60) }}</td>
        <td><span class="badge {{ $a->status==='pending'?'badge-warn':($a->status==='accepted'?'badge-green':'badge-red') }}">{{ $a->status }}</span></td>
        <td class="text-muted">{{ \Carbon\Carbon::parse($a->created_at)->format('Y-m-d') }}</td>
        <td style="display:flex;gap:0.4rem;">
            @if($a->status==='pending')
            <form method="POST" action="{{ route('dcc.moderation.appeals.accept', $a->id) }}">@csrf<button class="btn btn-sm btn-primary">Accept</button></form>
            <form method="POST" action="{{ route('dcc.moderation.appeals.deny', $a->id) }}">@csrf<button class="btn btn-sm btn-danger">Deny</button></form>
            @endif
        </td>
    </tr>
    @empty
        <tr><td colspan="5" class="text-muted" style="padding:1rem;">No appeals.</td></tr>
    @endforelse
    </tbody>
</table>
</div>
{{ $appeals->links() }}
@endsection
