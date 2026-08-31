@extends('layouts.app')
@section('title','The team')

@push('styles')
<style>
    .team { display: flex; flex-direction: column; gap: 1.2rem; }
    .team-grid {
        display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
        gap: 0.7rem;
    }
    .member {
        display: flex; gap: 0.7rem; align-items: center;
        padding: 0.7rem; background: var(--bg-deep);
        border: 1px solid var(--ridge); border-radius: 6px;
    }
    .member .figure { width: 44px; height: 66px; font-size: 1.1rem; }
    .member .who { min-width: 0; }
    .member .n { font-weight: 700; display: block; }
    .member .motto {
        display: block; font-size: 0.76rem; color: var(--muted);
        overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    .pip { display: inline-block; width: 7px; height: 7px; border-radius: 50%; }
    .pip-on { background: #4caf50; }
    .pip-off { background: var(--ridge); }
</style>
@endpush

@section('content')
<div style="max-width:860px;margin:0 auto;" class="team">

    <div class="panel">
        <div class="panel-head">
            <span>The team</span>
            <span class="text-muted" style="font-size:0.82rem;">{{ $onlineCount }} on duty</span>
        </div>
        <div class="panel-body">
            <p class="text-muted" style="font-size:0.86rem;">
                These are the people who look after the hotel. If somebody claims to be staff and
                is not on this page, they are not — and no member of staff will ever ask you for
                your password.
            </p>
        </div>
    </div>

    @forelse($groups as $rank => $members)
        <div class="panel">
            <div class="panel-head">
                <span>{{ $rank }}</span>
                <span class="text-muted" style="font-size:0.8rem;">{{ $members->count() }}</span>
            </div>
            <div class="panel-body">
                <div class="team-grid">
                    @foreach($members as $member)
                        <div class="member">
                            <div class="figure">
                                @if($member->avatarUrl())
                                    <img src="{{ $member->avatarUrl() }}" alt="">
                                @else
                                    {{ strtoupper(substr($member->username, 0, 1)) }}
                                @endif
                            </div>
                            <div class="who">
                                <a class="n" href="{{ route('profile.view', $member->username) }}">
                                    {{ $member->username }}
                                </a>
                                <span class="motto">{{ $member->motto ?: 'No motto set' }}</span>
                                <span class="motto">
                                    <i class="pip {{ $member->online ? 'pip-on' : 'pip-off' }}"></i>
                                    {{ $member->online ? 'On duty' : 'Away' }}
                                </span>
                            </div>
                        </div>
                    @endforeach
                </div>
            </div>
        </div>
    @empty
        <div class="panel"><div class="panel-body text-muted">No staff yet.</div></div>
    @endforelse

</div>
@endsection
