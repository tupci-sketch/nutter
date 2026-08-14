@extends('layouts.app')
@section('title', 'Terms of Service')
@section('content')
<div style="max-width:760px;margin:0 auto;">
    <h1>Terms of Service</h1>
    <p class="text-muted" style="margin-bottom:2rem;">Last updated: {{ date('F j, Y') }}</p>
    <div style="line-height:1.8;color:var(--text);">
        <h2>1. Acceptance</h2>
        <p>By creating a Habnut account you agree to these terms. If you do not agree, do not use the service.</p>
        <h2 style="margin-top:1.5rem;">2. Eligibility</h2>
        <p>You must be at least 13 years of age to use Habnut.</p>
        <h2 style="margin-top:1.5rem;">3. Account Rules</h2>
        <p>One account per person. You are responsible for all activity on your account. Do not share your login credentials.</p>
        <h2 style="margin-top:1.5rem;">4. Community Standards</h2>
        <p>Harassment, exploitation, real-money trading of virtual goods, and any illegal activity are prohibited. Violations result in suspension or permanent removal.</p>
        <h2 style="margin-top:1.5rem;">5. Virtual Currency</h2>
        <p>Credits, Diamonds, Nut Points, and Seasonal currency have no real-world monetary value and cannot be exchanged for cash. Nut Points may not be purchased.</p>
        <h2 style="margin-top:1.5rem;">6. Service Availability</h2>
        <p>We aim for continuous availability but cannot guarantee it. We reserve the right to modify or discontinue features at any time.</p>
        <h2 style="margin-top:1.5rem;">7. Changes</h2>
        <p>We may update these terms. Continued use after changes constitutes acceptance.</p>
    </div>
</div>
@endsection
