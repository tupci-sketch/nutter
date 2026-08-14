@extends('layouts.dcc')
@section('title', $user->username)
@section('content')
<h1>{{ $user->username }} <span class="badge badge-warn" style="font-size:1rem;">Rank {{ $user->rank }}</span></h1>
<p class="text-muted" style="margin-bottom:1.5rem;">ID: {{ $user->id }} &bull; {{ $user->email }} &bull; Joined {{ $user->created_at->format('Y-m-d') }}</p>

<div style="display:grid;grid-template-columns:1fr 1fr;gap:1.25rem;">
    <div>
        <div class="card">
            <h2>Adjust Rank</h2>
            <form method="POST" action="{{ route('dcc.users.rank', $user->id) }}" style="display:flex;gap:0.5rem;margin-top:0.75rem;">
                @csrf @method('PUT')
                <select name="rank" style="width:auto;">
                    @for($r=1;$r<=9;$r++)
                        <option value="{{ $r }}" {{ $user->rank==$r?'selected':'' }}>Rank {{ $r }}</option>
                    @endfor
                </select>
                <button type="submit" class="btn btn-primary btn-sm">Update</button>
            </form>
        </div>
        <div class="card">
            <h2>Currencies</h2>
            <table style="margin-bottom:1rem;">
                <tr><td>Credits</td><td>{{ number_format($user->credits) }}</td></tr>
                <tr><td>Diamonds</td><td>{{ number_format($user->diamonds) }}</td></tr>
                <tr><td>Nut Points</td><td>{{ number_format($user->nut_points) }}</td></tr>
            </table>
            <form method="POST" action="{{ route('dcc.users.credits', $user->id) }}" style="display:flex;gap:0.5rem;margin-bottom:0.5rem;">
                @csrf @method('PUT')
                <input type="number" name="amount" placeholder="±amount" style="width:110px;">
                <input type="text" name="reason" placeholder="Reason" style="flex:1;">
                <button type="submit" class="btn btn-sm btn-primary">Credits</button>
            </form>
            <form method="POST" action="{{ route('dcc.users.diamonds', $user->id) }}" style="display:flex;gap:0.5rem;">
                @csrf @method('PUT')
                <input type="number" name="amount" placeholder="±amount" style="width:110px;">
                <input type="text" name="reason" placeholder="Reason" style="flex:1;">
                <button type="submit" class="btn btn-sm btn-primary">Diamonds</button>
            </form>
        </div>
        @if(session('ticket'))
        <div class="card" style="border-color:var(--warn);">
            <h2>Issued Ticket</h2>
            <code style="font-size:0.8rem;word-break:break-all;">{{ session('ticket') }}</code>
            <p class="text-muted" style="font-size:0.75rem;margin-top:0.5rem;">Valid for 5 minutes.</p>
        </div>
        @endif
    </div>
    <div>
        <div class="card">
            <h2>Moderation</h2>
            @if(!$user->activeBan())
            <form method="POST" action="{{ route('dcc.users.ban', $user->id) }}" style="margin-top:0.75rem;">
                @csrf
                <div class="form-group">
                    <select name="type">
                        <option value="permanent">Permanent</option>
                        <option value="temporary">Temporary</option>
                    </select>
                </div>
                <div class="form-group">
                    <input type="datetime-local" name="expires_at">
                </div>
                <div class="form-group">
                    <input type="text" name="reason" placeholder="Ban reason" required>
                </div>
                <button type="submit" class="btn btn-danger btn-sm">Ban user</button>
            </form>
            @else
            <p class="text-muted" style="margin:0.75rem 0;">User is currently banned: {{ $user->activeBan()->reason }}</p>
            <form method="POST" action="{{ route('dcc.users.unban', $user->id) }}">
                @csrf
                <button type="submit" class="btn btn-primary btn-sm">Lift ban</button>
            </form>
            @endif
        </div>
        <div class="card">
            <h2>Recent Bans</h2>
            @if($bans->isEmpty())
                <p class="text-muted" style="font-size:0.85rem;">No bans.</p>
            @else
            <table style="font-size:0.8rem;">
                <thead><tr><th>Reason</th><th>Type</th><th>Active</th><th>Date</th></tr></thead>
                <tbody>
                @foreach($bans->take(5) as $b)
                <tr>
                    <td>{{ $b->reason }}</td>
                    <td>{{ $b->type }}</td>
                    <td><span class="badge {{ $b->active ? 'badge-red' : 'badge-green' }}">{{ $b->active ? 'Yes' : 'No' }}</span></td>
                    <td>{{ \Carbon\Carbon::parse($b->created_at)->format('Y-m-d') }}</td>
                </tr>
                @endforeach
                </tbody>
            </table>
            @endif
        </div>
    </div>
</div>
@endsection
