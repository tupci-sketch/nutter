@extends('layouts.app')
@section('title', 'Privacy Policy')
@section('content')
<div style="max-width:760px;margin:0 auto;">
    <h1>Privacy Policy</h1>
    <p class="text-muted" style="margin-bottom:2rem;">Last updated: {{ date('F j, Y') }}</p>
    <div style="line-height:1.8;color:var(--text);">
        <h2>1. Information We Collect</h2>
        <p>We collect information you provide when registering (username, email), plus technical data such as IP addresses and session identifiers necessary for operating the service.</p>
        <h2 style="margin-top:1.5rem;">2. How We Use Your Information</h2>
        <p>Your information is used solely to operate Habnut: account management, security, in-hotel services, and support. We do not sell your data.</p>
        <h2 style="margin-top:1.5rem;">3. Data Retention</h2>
        <p>Account data is retained while your account is active. Chat logs are retained for 90 days for safety purposes. Audit logs are retained for 365 days.</p>
        <h2 style="margin-top:1.5rem;">4. Your Rights</h2>
        <p>You may request access to or deletion of your data at any time by contacting support.</p>
        <h2 style="margin-top:1.5rem;">5. Contact</h2>
        <p>Questions? Contact our support team through the Help section.</p>
    </div>
</div>
@endsection
