@extends('layouts.dcc')
@section('title','Automatic mutes')
@section('content')
<h1>Automatic mutes</h1>

<p class="text-muted" style="font-size:0.86rem;max-width:640px;">
    A message the content policy stops silences its sender straight away, because harm left
    standing while somebody waits for a moderator has already been done. That is only fair if
    a person looks afterwards — which is this page. Read what was actually said, then uphold
    the mute or lift it.
</p>

<div style="display:flex;gap:0.4rem;margin:1rem 0;align-items:center;">
    @foreach(['pending_review' => 'Waiting', 'upheld' => 'Upheld', 'overturned' => 'Lifted', 'all' => 'All'] as $key => $label)
        <a class="btn btn-sm {{ $status === $key ? 'btn-primary' : '' }}"
           href="{{ route('dcc.automod.index', ['status' => $key]) }}">{{ $label }}</a>
    @endforeach
    <span class="text-muted" style="margin-left:auto;font-size:0.85rem;">
        {{ $pending }} waiting &middot; {{ $waitingOnHelp }} asked for help
    </span>
</div>

@forelse($cases as $case)
    <div class="card" style="margin-bottom:0.8rem;">
        <div style="display:flex;gap:0.6rem;align-items:center;flex-wrap:wrap;">
            <span class="badge {{ in_array($case->category, ['minor_safety','hate']) ? 'badge-red' : 'badge-amber' }}">
                {{ str_replace('_', ' ', $case->category) }}
            </span>
            <strong>{{ $case->username ?? 'unknown' }}</strong>
            <span class="text-muted" style="font-size:0.8rem;">
                {{ \Carbon\Carbon::parse($case->created_at)->diffForHumans() }}
                @if($case->room_id) &middot; room {{ $case->room_id }} @endif
            </span>
            <span class="text-muted" style="font-size:0.8rem;margin-left:auto;">
                {{ $case->label ?? 'rule removed' }}
            </span>
        </div>

        <blockquote style="margin:0.7rem 0;padding:0.6rem 0.8rem;background:var(--bg3);
                           border-left:3px solid var(--rose);border-radius:4px;
                           white-space:pre-wrap;overflow-wrap:anywhere;">{{ $case->message }}</blockquote>

        @if($case->help_requests->isNotEmpty())
            <div style="margin-bottom:0.7rem;">
                <div class="text-muted" style="font-size:0.75rem;text-transform:uppercase;
                            letter-spacing:0.08em;margin-bottom:0.3rem;">Asked for help</div>
                @foreach($case->help_requests as $help)
                    <div style="font-size:0.85rem;margin-bottom:0.25rem;">
                        “{{ $help->message }}”
                        <span class="text-muted">
                            — {{ \Carbon\Carbon::parse($help->created_at)->diffForHumans() }}
                        </span>
                    </div>
                @endforeach
            </div>
        @endif

        @if($case->status === 'pending_review')
            <form method="POST" action="{{ route('dcc.automod.review', $case->id) }}"
                  style="display:flex;gap:0.4rem;align-items:center;flex-wrap:wrap;">
                @csrf
                <input name="notes" maxlength="512" placeholder="Note for the record (optional)"
                       style="flex:1;min-width:200px;">
                <button class="btn btn-sm btn-danger" type="submit" name="decision" value="uphold">
                    Uphold mute
                </button>
                <button class="btn btn-sm" type="submit" name="decision" value="overturn">
                    Lift mute
                </button>
            </form>
        @else
            <div style="font-size:0.85rem;">
                <span class="badge {{ $case->status === 'upheld' ? 'badge-red' : 'badge-green' }}">
                    {{ $case->status === 'upheld' ? 'Upheld' : 'Lifted' }}
                </span>
                @if($case->review_notes)
                    <span class="text-muted">{{ $case->review_notes }}</span>
                @endif
                @if($case->reviewed_at)
                    <span class="text-muted">
                        &middot; {{ \Carbon\Carbon::parse($case->reviewed_at)->diffForHumans() }}
                    </span>
                @endif
            </div>
        @endif
    </div>
@empty
    <div class="card text-muted">Nothing here.</div>
@endforelse

{{ $cases->links() }}
@endsection
