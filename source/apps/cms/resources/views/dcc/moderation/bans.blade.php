@extends('layouts.dcc')
@section('title', 'Active Bans')
@section('content')
<h1>Active Bans</h1>
<div class="card" style="padding:0;">
<table>
    <thead><tr><th>User</th><th>By</th><th>Reason</th><th>Type</th><th>Expires</th><th></th></tr></thead>
    <tbody>
    @forelse($bans as $b)
    <tr>
        <td><a href="{{ route('dcc.users.show', $b->user->id) }}">{{ $b->user->username }}</a></td>
        <td class="text-muted">{{ $b->staff->username ?? '—' }}</td>
        <td>{{ \Illuminate\Support\Str::limit($b->reason, 50) }}</td>
        <td>{{ $b->type }}</td>
        <td class="text-muted">{{ $b->expires_at ? \Carbon\Carbon::parse($b->expires_at)->format('Y-m-d') : 'Never' }}</td>
        <td>
            <form method="POST" action="{{ route('dcc.moderation.bans.lift', $b->id) }}" style="display:inline;">
                @csrf
                <button type="submit" class="btn btn-sm btn-danger">Lift</button>
            </form>
        </td>
    </tr>
    @empty
        <tr><td colspan="6" class="text-muted" style="padding:1rem;">No active bans.</td></tr>
    @endforelse
    </tbody>
</table>
</div>
{{ $bans->links() }}
@endsection
