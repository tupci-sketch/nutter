<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <title>DCC — @yield('title', 'Dashboard') — {{ config('habnut.brand_name') }}</title>
    <style>
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
        :root {
            --bg: #0d0d1a; --bg2: #13132a; --bg3: #1a1a3a; --sidebar: #10101f;
            --accent: #f0a040; --accent2: #7c6af0;
            --text: #e0daf0; --muted: #666; --border: #222;
            --success: #4caf50; --danger: #e94560; --warn: #f0a040;
        }
        body { font-family: 'Segoe UI', system-ui, sans-serif; background: var(--bg); color: var(--text); display: flex; min-height: 100vh; font-size: 14px; }
        a { color: var(--accent); text-decoration: none; }
        a:hover { text-decoration: underline; }
        aside { width: 220px; background: var(--sidebar); border-right: 1px solid var(--border); display: flex; flex-direction: column; flex-shrink: 0; position: sticky; top: 0; height: 100vh; overflow-y: auto; }
        aside .brand { padding: 1.2rem 1rem; font-size: 1rem; font-weight: 700; color: var(--accent); font-family: monospace; border-bottom: 1px solid var(--border); }
        aside .brand small { display: block; color: var(--muted); font-size: 0.7rem; font-weight: 400; font-family: sans-serif; }
        aside nav a { display: block; padding: 0.55rem 1rem; color: var(--text); font-size: 0.85rem; }
        aside nav a:hover { background: var(--bg3); color: var(--accent); text-decoration: none; }
        aside nav .section { padding: 0.75rem 1rem 0.25rem; color: var(--muted); font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.07em; }
        main { flex: 1; padding: 2rem; overflow: auto; }
        main h1 { font-size: 1.5rem; margin-bottom: 1.5rem; color: var(--text); }
        main h2 { font-size: 1.1rem; margin-bottom: 1rem; }
        .alert { padding: 0.75rem 1rem; border-radius: 6px; margin-bottom: 1rem; font-size: 0.85rem; }
        .alert-success { background: rgba(76,175,80,0.1); border: 1px solid var(--success); color: var(--success); }
        .alert-error { background: rgba(233,69,96,0.1); border: 1px solid var(--danger); color: var(--danger); }
        .card { background: var(--bg2); border: 1px solid var(--border); border-radius: 8px; padding: 1.25rem; margin-bottom: 1.25rem; }
        .stats-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
        .stat-card { background: var(--bg2); border: 1px solid var(--border); border-radius: 8px; padding: 1rem; }
        .stat-card .label { color: var(--muted); font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.05em; }
        .stat-card .value { font-size: 1.8rem; font-weight: 700; color: var(--accent); margin-top: 0.25rem; }
        table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
        th, td { text-align: left; padding: 0.55rem 0.75rem; border-bottom: 1px solid var(--border); }
        th { color: var(--muted); font-weight: 600; font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.04em; }
        tr:hover td { background: var(--bg3); }
        input, select, textarea { background: var(--bg3); border: 1px solid var(--border); color: var(--text); padding: 0.45rem 0.7rem; border-radius: 5px; font-family: inherit; font-size: 0.85rem; width: 100%; }
        input:focus, select:focus, textarea:focus { outline: none; border-color: var(--accent); }
        label { display: block; margin-bottom: 0.25rem; font-size: 0.8rem; color: var(--muted); }
        .form-group { margin-bottom: 0.85rem; }
        .btn { display: inline-block; padding: 0.45rem 1rem; border-radius: 5px; border: none; cursor: pointer; font-size: 0.85rem; font-family: inherit; }
        .btn-primary { background: var(--accent); color: var(--bg); font-weight: 600; }
        .btn-danger  { background: var(--danger); color: #fff; }
        .btn-sm { padding: 0.25rem 0.6rem; font-size: 0.75rem; }
        .badge { display: inline-block; padding: 0.1rem 0.45rem; border-radius: 99px; font-size: 0.7rem; font-weight: 600; }
        .badge-green { background: rgba(76,175,80,0.2); color: var(--success); }
        .badge-red   { background: rgba(233,69,96,0.2); color: var(--danger); }
        .badge-warn  { background: rgba(240,160,64,0.2); color: var(--warn); }
        .text-muted { color: var(--muted); }
        .pagination { display: flex; gap: 0.5rem; margin-top: 1rem; font-size: 0.85rem; }
        .pagination a, .pagination span { padding: 0.3rem 0.7rem; border: 1px solid var(--border); border-radius: 4px; }
        .pagination span.active { background: var(--accent); color: var(--bg); border-color: var(--accent); }
    </style>
    @stack('styles')
</head>
<body>
<aside>
    <div class="brand">
        🌰 {{ config('habnut.brand_name') }}<br>
        <small>Development Control Centre</small>
    </div>
    <nav>
        <div class="section">Overview</div>
        <a href="{{ route('dcc.dashboard') }}">Dashboard</a>

        <div class="section">Users</div>
        <a href="{{ route('dcc.users.index') }}">All Users</a>

        <div class="section">Economy</div>
        <a href="{{ route('dcc.economy.index') }}">Overview</a>
        <a href="{{ route('dcc.economy.transactions') }}">Transactions</a>
        <a href="{{ route('dcc.economy.stats') }}">Stats</a>
        <a href="{{ route('dcc.catalogue.index') }}">Catalogue</a>

        <div class="section">World</div>
        <a href="{{ route('dcc.rooms.index') }}">Rooms</a>
        <a href="{{ route('dcc.groups.index') }}">Groups</a>
        <a href="{{ route('dcc.wired.index') }}">Wired</a>
        <a href="{{ route('dcc.games.index') }}">Games</a>
        <a href="{{ route('dcc.garden.index') }}">Garden</a>

        <div class="section">Nutropolis</div>
        <a href="{{ route('dcc.rp.index') }}">RP Overview</a>
        <a href="{{ route('dcc.rp.characters') }}">Characters</a>
        <a href="{{ route('dcc.rp.court') }}">Court</a>
        <a href="{{ route('dcc.rp.bank') }}">Bank</a>
        <a href="{{ route('dcc.rp.laws') }}">Laws</a>

        <div class="section">Trust &amp; Safety</div>
        <a href="{{ route('dcc.moderation.index') }}">Overview</a>
        <a href="{{ route('dcc.moderation.reports') }}">Reports</a>
        <a href="{{ route('dcc.moderation.bans') }}">Bans</a>
        <a href="{{ route('dcc.moderation.appeals') }}">Appeals</a>
        <a href="{{ route('dcc.moderation.wordfilter') }}">Word Filter</a>
        <a href="{{ route('dcc.moderation.chatlogs') }}">Chat Logs</a>

        <div class="section">Staff</div>
        <a href="{{ route('dcc.staff.index') }}">Staff List</a>
        <a href="{{ route('dcc.staff.actions') }}">Actions</a>

        <div class="section">System</div>
        <a href="{{ route('dcc.system.settings') }}">Settings</a>
        <a href="{{ route('dcc.system.flags') }}">Feature Flags</a>
        <a href="{{ route('dcc.system.monitoring') }}">Monitoring</a>
        <a href="{{ route('dcc.system.audit') }}">Audit Log</a>
        <a href="{{ route('dcc.system.maintenance') }}">Maintenance</a>

        <div class="section">Account</div>
        <a href="{{ route('profile.show') }}">My Profile</a>
        <form method="POST" action="{{ route('logout') }}" style="margin: 0.2rem 0;">
            @csrf
            <button type="submit" style="background: none; border: none; color: var(--muted); cursor: pointer; font-size: 0.85rem; padding: 0.55rem 1rem; width: 100%; text-align: left;">Sign out</button>
        </form>
    </nav>
</aside>
<main>
    @if(session('success'))
        <div class="alert alert-success">{{ session('success') }}</div>
    @endif
    @if($errors->any())
        <div class="alert alert-error">
            @foreach($errors->all() as $e) {{ $e }}<br> @endforeach
        </div>
    @endif
    @yield('content')
</main>
</body>
</html>
