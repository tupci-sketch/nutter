@extends('layouts.dcc')
@section('title','Forum reports')
@section('content')
<h1>Forum reports</h1>

<div style="display:flex;gap:0.4rem;margin-bottom:1rem;">
    @foreach(['open' => 'Open', 'upheld' => 'Upheld', 'dismissed' => 'Dismissed', 'all' => 'All'] as $key => $label)
        <a class="btn btn-sm {{ $status === $key ? 'btn-primary' : '' }}"
           href="{{ route('dcc.forum.reports', ['status' => $key]) }}">{{ $label }}</a>
    @endforeach
</div>

<div class="card" style="padding:0;"><table>
<thead><tr><th>Reported</th><th>Post</th><th>Reason</th><th>When</th><th></th></tr></thead>
<tbody>
@forelse($reports as $r)
<tr>
    <td>
        {{ $r->post?->author?->username ?? 'unknown' }}<br>
        <span class="text-muted" style="font-size:0.78rem;">by {{ $r->reporter?->username ?? 'unknown' }}</span>
    </td>
    <td style="max-width:340px;">
        @if($r->post?->thread)
            <a href="{{ route('forum.thread', $r->post->thread_id) }}">{{ $r->post->thread->title }}</a><br>
        @endif
        <span class="text-muted" style="font-size:0.8rem;">{{ Str::limit($r->post?->body, 160) }}</span>
        @if($r->post?->hidden)
            <br><span class="badge badge-red">Hidden</span>
        @endif
    </td>
    <td style="max-width:220px;">{{ $r->reason }}</td>
    <td class="text-muted">{{ $r->created_at?->diffForHumans() }}</td>
    <td>
        @if($r->status === 'open')
            <form method="POST" action="{{ route('dcc.forum.report.resolve', $r->id) }}"
                  style="display:grid;gap:0.35rem;min-width:200px;">
                @csrf
                <input name="notes" maxlength="512" placeholder="Note (optional)">
                <div style="display:flex;gap:0.35rem;">
                    <button class="btn btn-sm btn-danger" type="submit" name="decision" value="uphold">Uphold</button>
                    <button class="btn btn-sm" type="submit" name="decision" value="dismiss">Dismiss</button>
                </div>
            </form>
        @else
            <span class="badge {{ $r->status === 'upheld' ? 'badge-red' : 'badge-green' }}">{{ ucfirst($r->status) }}</span>
            @if($r->post?->hidden)
                <form method="POST" action="{{ route('dcc.forum.post.restore', $r->post_id) }}" style="margin-top:0.35rem;">
                    @csrf
                    <button class="btn btn-sm" type="submit">Restore post</button>
                </form>
            @endif
        @endif
    </td>
</tr>
@empty
<tr><td colspan="5" class="text-muted">Nothing here.</td></tr>
@endforelse
</tbody></table></div>
{{ $reports->links() }}
@endsection
