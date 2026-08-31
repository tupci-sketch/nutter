@extends('layouts.dcc')
@section('title', 'Users')
@section('content')
<h1>Users</h1>
<form method="GET" style="display:flex;gap:0.75rem;margin-bottom:1.25rem;">
    <input type="text" name="q" value="{{ request('q') }}" placeholder="Search username or email…" style="max-width:300px;">
    <select name="rank" style="max-width:140px;">
        <option value="">All ranks</option>
        @for($r=1;$r<=9;$r++)
            <option value="{{ $r }}" {{ request('rank')==$r?'selected':'' }}>Rank {{ $r }}</option>
        @endfor
    </select>
    <button type="submit" class="btn btn-primary btn-sm">Search</button>
</form>
<div class="card" style="padding:0;">
    <table>
        <thead><tr><th>ID</th><th>Username</th><th>Email</th><th>Rank</th><th>Credits</th><th>Joined</th><th></th></tr></thead>
        <tbody>
            @foreach($users as $u)
            <tr>
                <td class="text-muted">{{ $u->id }}</td>
                <td>{{ $u->username }}</td>
                <td class="text-muted">{{ $u->email }}</td>
                <td><span class="badge badge-warn">{{ $u->rank }}</span></td>
                <td>{{ number_format($u->credits) }}</td>
                <td class="text-muted">{{ \Carbon\Carbon::parse($u->created_at)->format('Y-m-d') }}</td>
                <td><a href="{{ route('dcc.users.show', $u->id) }}" class="btn btn-sm" style="background:var(--bg3);color:var(--text);">View</a></td>
            </tr>
            @endforeach
        </tbody>
    </table>
</div>
<div class="pagination">{{ $users->withQueryString()->links() }}</div>
@endsection
