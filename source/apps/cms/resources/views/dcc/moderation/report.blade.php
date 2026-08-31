@extends('layouts.dcc')
@section('title', 'Report #' . $report->id)
@section('content')
<h1>Report #{{ $report->id }}</h1>
<div style="display:grid;grid-template-columns:1fr 1fr;gap:1.25rem;">
    <div class="card">
        <h2>Details</h2>
        <table style="margin-top:0.75rem;">
            <tr><td class="text-muted">Reporter</td><td>{{ $report->reporter_name }}</td></tr>
            <tr><td class="text-muted">Reported</td><td>{{ $report->reported_name }}</td></tr>
            <tr><td class="text-muted">Reason</td><td>{{ $report->reason ?? '—' }}</td></tr>
            <tr><td class="text-muted">Status</td><td><span class="badge {{ $report->status==='open'?'badge-red':'badge-green' }}">{{ $report->status }}</span></td></tr>
            <tr><td class="text-muted">Created</td><td>{{ \Carbon\Carbon::parse($report->created_at)->format('Y-m-d H:i') }}</td></tr>
        </table>
        @if($report->status==='open')
        <form method="POST" action="{{ route('dcc.moderation.resolve', $report->id) }}" style="margin-top:1rem;">
            @csrf
            <div class="form-group">
                <label>Resolution note</label>
                <textarea name="resolution" rows="3" required></textarea>
            </div>
            <div class="form-group">
                <label>Action taken</label>
                <select name="action">
                    <option value="none">No action</option>
                    <option value="warn">Warn</option>
                    <option value="mute">Mute</option>
                    <option value="ban">Ban</option>
                </select>
            </div>
            <button type="submit" class="btn btn-primary btn-sm">Resolve</button>
        </form>
        @endif
    </div>
    <div class="card">
        <h2>Recent Chat ({{ $report->reported_name }})</h2>
        <div style="max-height:300px;overflow-y:auto;margin-top:0.75rem;">
            @forelse($chatContext as $msg)
            <div style="padding:0.35rem 0;border-bottom:1px solid var(--border);font-size:0.82rem;">
                <span class="text-muted">{{ \Carbon\Carbon::parse($msg->created_at)->format('H:i') }}</span>
                {{ $msg->message }}
            </div>
            @empty
            <p class="text-muted" style="font-size:0.85rem;">No recent chat.</p>
            @endforelse
        </div>
    </div>
</div>
@endsection
