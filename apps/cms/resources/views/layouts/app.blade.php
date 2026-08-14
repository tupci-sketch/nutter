<!DOCTYPE html>
<html lang="{{ str_replace('_', '-', app()->getLocale()) }}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <title>{{ config('habnut.brand_name') }} — @yield('title', 'Home')</title>
    <style>
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
        :root {
            --bg: #1a1a2e; --bg2: #16213e; --bg3: #0f3460;
            --accent: #f0a040; --accent2: #e94560;
            --text: #e8e0d0; --muted: #888; --border: #333;
            --success: #4caf50; --danger: #e94560; --warn: #f0a040;
        }
        body { font-family: 'Segoe UI', system-ui, sans-serif; background: var(--bg); color: var(--text); min-height: 100vh; }
        a { color: var(--accent); text-decoration: none; }
        a:hover { text-decoration: underline; }
        nav.main-nav { background: var(--bg2); border-bottom: 1px solid var(--border); padding: 0 2rem; display: flex; align-items: center; gap: 1.5rem; height: 56px; }
        nav.main-nav .brand { font-size: 1.3rem; font-weight: 700; color: var(--accent); font-family: monospace; }
        nav.main-nav .spacer { flex: 1; }
        nav.main-nav a { color: var(--text); font-size: 0.9rem; }
        nav.main-nav a:hover { color: var(--accent); text-decoration: none; }
        .container { max-width: 1100px; margin: 0 auto; padding: 2rem 1.5rem; }
        .alert { padding: 0.75rem 1rem; border-radius: 6px; margin-bottom: 1rem; font-size: 0.9rem; }
        .alert-success { background: rgba(76,175,80,0.15); border: 1px solid var(--success); color: var(--success); }
        .alert-error { background: rgba(233,69,96,0.15); border: 1px solid var(--danger); color: var(--danger); }
        .btn { display: inline-block; padding: 0.5rem 1.2rem; border-radius: 6px; border: none; cursor: pointer; font-size: 0.9rem; font-family: inherit; }
        .btn-primary { background: var(--accent); color: var(--bg); font-weight: 600; }
        .btn-danger  { background: var(--danger); color: #fff; }
        .btn-sm { padding: 0.3rem 0.8rem; font-size: 0.8rem; }
        .card { background: var(--bg2); border: 1px solid var(--border); border-radius: 10px; padding: 1.5rem; margin-bottom: 1.5rem; }
        h1 { font-size: 1.8rem; margin-bottom: 1rem; }
        h2 { font-size: 1.3rem; margin-bottom: 0.75rem; }
        table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
        th, td { text-align: left; padding: 0.6rem 0.8rem; border-bottom: 1px solid var(--border); }
        th { color: var(--muted); font-weight: 600; font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.05em; }
        input, select, textarea { background: #2a2a3e; border: 1px solid var(--border); color: var(--text); padding: 0.5rem 0.75rem; border-radius: 6px; font-family: inherit; font-size: 0.9rem; width: 100%; }
        input:focus, select:focus, textarea:focus { outline: none; border-color: var(--accent); }
        label { display: block; margin-bottom: 0.3rem; font-size: 0.85rem; color: var(--muted); }
        .form-group { margin-bottom: 1rem; }
        .text-muted { color: var(--muted); }
        .text-danger { color: var(--danger); }
        .text-success { color: var(--success); }
        .badge { display: inline-block; padding: 0.15rem 0.5rem; border-radius: 99px; font-size: 0.75rem; font-weight: 600; }
        .badge-rank { background: rgba(240,160,64,0.2); color: var(--accent); }
        footer { border-top: 1px solid var(--border); padding: 1.5rem 2rem; text-align: center; color: var(--muted); font-size: 0.8rem; margin-top: 4rem; }
    </style>
    @stack('styles')
</head>
<body>
<nav class="main-nav">
    <span class="brand">{{ config('habnut.brand_acorn') }} {{ config('habnut.brand_name') }}</span>
    <a href="{{ route('home') }}">Home</a>
    <a href="{{ route('news.index') }}">News</a>
    <a href="{{ route('hotel') }}">Hotel</a>
    <a href="{{ route('help.index') }}">Help</a>
    <span class="spacer"></span>
    @auth
        <a href="{{ route('profile.show') }}">{{ auth()->user()->username }}</a>
        @if(auth()->user()->isStaff())
            <a href="{{ route('dcc.dashboard') }}">DCC</a>
        @endif
        <form method="POST" action="{{ route('logout') }}" style="display:inline">
            @csrf
            <button type="submit" class="btn btn-sm" style="background:transparent;color:var(--muted);cursor:pointer;">Sign out</button>
        </form>
    @else
        <a href="{{ route('login') }}">Login</a>
        <a href="{{ route('register') }}" class="btn btn-primary btn-sm">Register</a>
    @endauth
</nav>

<div class="container">
    @if(session('success'))
        <div class="alert alert-success">{{ session('success') }}</div>
    @endif
    @if($errors->any())
        <div class="alert alert-error">
            @foreach($errors->all() as $e) {{ $e }}<br> @endforeach
        </div>
    @endif

    @yield('content')
</div>

<footer>
    &copy; {{ date('Y') }} {{ config('habnut.brand_name') }} &mdash;
    <a href="{{ route('privacy') }}">Privacy</a> &bull;
    <a href="{{ route('terms') }}">Terms</a>
</footer>
</body>
</html>
