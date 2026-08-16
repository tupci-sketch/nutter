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
            /* Archive palette: deep navy ground, brass accent, parchment text. */
            --bg:        #12142a;
            --bg-deep:   #0c0e1e;
            --panel:     #1b2044;
            --panel-2:   #232a52;
            --ridge:     #2f3767;
            --brass:     #f0a040;
            --brass-dim: #b8792c;
            --rose:      #e94560;
            --jade:      #47b881;
            --text:      #ece4d4;
            --muted:     #8b91b4;

            --shell: 1080px;
            --mono: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
            --sans: "Segoe UI", system-ui, -apple-system, sans-serif;
        }

        body {
            font-family: var(--sans);
            background:
                radial-gradient(1200px 500px at 50% -10%, #1d2350 0%, transparent 70%),
                var(--bg);
            color: var(--text);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
        }

        a { color: var(--brass); text-decoration: none; }
        a:hover { text-decoration: underline; }
        :focus-visible { outline: 2px solid var(--brass); outline-offset: 2px; border-radius: 3px; }

        /* ── header ─────────────────────────────────────────────────────── */
        .topbar {
            background: linear-gradient(180deg, var(--panel-2), var(--panel));
            border-bottom: 2px solid var(--ridge);
            box-shadow: 0 2px 0 rgba(0,0,0,0.35);
        }
        .topbar-inner {
            max-width: var(--shell); margin: 0 auto;
            padding: 0 1.25rem; height: 62px;
            display: flex; align-items: center; gap: 1.5rem;
        }
        .brand {
            font-family: var(--mono); font-size: 1.25rem; font-weight: 700;
            color: var(--brass); letter-spacing: -0.02em;
            display: flex; align-items: center; gap: 0.45rem; white-space: nowrap;
        }
        .nav-links { display: flex; gap: 1.1rem; flex: 1; }
        .nav-links a {
            color: var(--text); font-size: 0.92rem; padding: 0.3rem 0;
            border-bottom: 2px solid transparent;
        }
        .nav-links a:hover { color: var(--brass); border-bottom-color: var(--brass-dim); text-decoration: none; }

        /* Currency badges, the thing a player checks most often. */
        .purse { display: flex; gap: 0.5rem; align-items: center; }
        .coin {
            display: inline-flex; align-items: center; gap: 0.35rem;
            background: var(--bg-deep); border: 1px solid var(--ridge);
            border-radius: 999px; padding: 0.25rem 0.7rem;
            font-family: var(--mono); font-size: 0.82rem;
            font-variant-numeric: tabular-nums; white-space: nowrap;
        }
        .coin .dot { width: 9px; height: 9px; border-radius: 50%; flex: none; }
        .coin.credits  .dot { background: var(--brass); }
        .coin.diamonds .dot { background: #6fd3f5; }
        .coin.points   .dot { background: var(--jade); }

        .who { display: flex; align-items: center; gap: 0.6rem; }
        .who .name { font-weight: 600; font-size: 0.9rem; }

        /* ── shell ──────────────────────────────────────────────────────── */
        .shell { max-width: var(--shell); margin: 0 auto; padding: 1.75rem 1.25rem 3rem; width: 100%; flex: 1; }

        .alert { padding: 0.7rem 1rem; border-radius: 6px; margin-bottom: 1.1rem; font-size: 0.9rem; border: 1px solid; }
        .alert-success { background: rgba(71,184,129,0.12); border-color: var(--jade); color: var(--jade); }
        .alert-error   { background: rgba(233,69,96,0.12);  border-color: var(--rose); color: #ff8fa3; }

        /* ── panels ─────────────────────────────────────────────────────── */
        .panel {
            background: var(--panel);
            border: 1px solid var(--ridge);
            border-radius: 8px;
            overflow: hidden;
        }
        .panel-head {
            background: var(--panel-2);
            border-bottom: 1px solid var(--ridge);
            padding: 0.6rem 1rem;
            font-family: var(--mono); font-size: 0.72rem;
            letter-spacing: 0.14em; text-transform: uppercase; color: var(--muted);
            display: flex; align-items: center; justify-content: space-between; gap: 0.75rem;
        }
        .panel-body { padding: 1.1rem; }

        /* ── buttons ────────────────────────────────────────────────────── */
        .btn {
            display: inline-block; padding: 0.55rem 1.2rem; border-radius: 6px;
            border: 1px solid var(--ridge); background: var(--panel-2); color: var(--text);
            cursor: pointer; font-size: 0.9rem; font-family: inherit; text-align: center;
        }
        .btn:hover { border-color: var(--brass-dim); text-decoration: none; }
        .btn-primary {
            background: linear-gradient(180deg, var(--brass), var(--brass-dim));
            border-color: var(--brass-dim); color: #241503; font-weight: 700;
        }
        .btn-danger { background: var(--rose); border-color: var(--rose); color: #fff; }
        .btn-sm { padding: 0.28rem 0.7rem; font-size: 0.8rem; }
        .btn-block { display: block; width: 100%; }

        /* ── avatar ─────────────────────────────────────────────────────── */
        .figure {
            width: 64px; height: 110px; flex: none;
            display: grid; place-items: center;
            background: linear-gradient(180deg, var(--panel-2), var(--bg-deep));
            border: 1px solid var(--ridge); border-radius: 6px;
            font-family: var(--mono); font-size: 1.6rem; font-weight: 700; color: var(--brass);
            image-rendering: pixelated;
        }
        .figure img { width: 100%; height: 100%; object-fit: contain; image-rendering: pixelated; }
        .figure-sm { width: 34px; height: 52px; font-size: 0.95rem; }

        /* ── misc ───────────────────────────────────────────────────────── */
        table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
        th, td { text-align: left; padding: 0.55rem 0.7rem; border-bottom: 1px solid var(--ridge); }
        th { color: var(--muted); font-family: var(--mono); font-size: 0.7rem;
             text-transform: uppercase; letter-spacing: 0.1em; font-weight: 600; }
        tbody tr:last-child td { border-bottom: none; }

        input, select, textarea {
            background: var(--bg-deep); border: 1px solid var(--ridge); color: var(--text);
            padding: 0.5rem 0.7rem; border-radius: 6px; font-family: inherit;
            font-size: 0.9rem; width: 100%;
        }
        input:focus, select:focus, textarea:focus { outline: none; border-color: var(--brass); }
        label { display: block; margin-bottom: 0.3rem; font-size: 0.85rem; color: var(--muted); }
        .form-group { margin-bottom: 1rem; }

        .badge { display: inline-block; padding: 0.14rem 0.5rem; border-radius: 999px;
                 font-size: 0.72rem; font-weight: 700; font-family: var(--mono); }
        .badge-rank  { background: rgba(240,160,64,0.16); color: var(--brass); }
        .badge-staff { background: rgba(233,69,96,0.16);  color: #ff8fa3; }

        .pip { width: 8px; height: 8px; border-radius: 50%; display: inline-block; flex: none; }
        .pip-on  { background: var(--jade); box-shadow: 0 0 6px rgba(71,184,129,0.8); }
        .pip-off { background: #4a5178; }

        .text-muted { color: var(--muted); }
        .stack { display: flex; flex-direction: column; gap: 1.25rem; }
        h1 { font-size: 1.7rem; margin-bottom: 0.9rem; letter-spacing: -0.01em; }
        h2 { font-size: 1.15rem; margin-bottom: 0.6rem; }

        footer {
            border-top: 1px solid var(--ridge); background: var(--bg-deep);
            padding: 1.3rem; text-align: center; color: var(--muted); font-size: 0.8rem;
        }

        @media (max-width: 720px) {
            .topbar-inner { height: auto; flex-wrap: wrap; padding: 0.7rem 1rem; gap: 0.7rem; }
            .nav-links { order: 3; width: 100%; overflow-x: auto; }
        }
        @media (prefers-reduced-motion: reduce) { * { transition: none !important; animation: none !important; } }
    </style>
    @stack('styles')
</head>
<body>

<header class="topbar">
    <div class="topbar-inner">
        <a href="{{ route('home') }}" class="brand">
            {{ config('habnut.brand_acorn') }} {{ config('habnut.brand_name') }}
        </a>

        <nav class="nav-links">
            <a href="{{ route('home') }}">Home</a>
            <a href="{{ route('news.index') }}">News</a>
            <a href="{{ route('hotel') }}">Hotel</a>
            <a href="{{ route('help.index') }}">Help</a>
        </nav>

        @auth
            <div class="purse">
                <span class="coin credits"  title="Credits"><i class="dot"></i>{{ number_format(auth()->user()->credits) }}</span>
                <span class="coin diamonds" title="Diamonds"><i class="dot"></i>{{ number_format(auth()->user()->diamonds) }}</span>
                <span class="coin points"   title="Nut Points"><i class="dot"></i>{{ number_format(auth()->user()->nut_points) }}</span>
            </div>
            <div class="who">
                <a href="{{ route('profile.show') }}" class="name">{{ auth()->user()->username }}</a>
                @if(auth()->user()->isStaff())
                    <a href="{{ route('dcc.dashboard') }}" class="badge badge-staff">DCC</a>
                @endif
                <form method="POST" action="{{ route('logout') }}">
                    @csrf
                    <button type="submit" class="btn btn-sm">Sign out</button>
                </form>
            </div>
        @else
            <div class="who">
                <a href="{{ route('login') }}">Login</a>
                <a href="{{ route('register') }}" class="btn btn-primary btn-sm">Register</a>
            </div>
        @endauth
    </div>
</header>

<div class="shell">
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
    &copy; {{ date('Y') }} {{ config('habnut.brand_name') }} &middot;
    <a href="{{ route('privacy') }}">Privacy</a> &middot;
    <a href="{{ route('terms') }}">Terms</a>
</footer>

</body>
</html>
