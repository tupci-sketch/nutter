@extends('layouts.dcc')
@section('title', 'Feature Flags')
@section('content')
<h1>Feature Flags</h1>
<div class="card" style="padding:0;">
<table>
    <thead><tr><th>Key</th><th>Description</th><th>Status</th><th></th></tr></thead>
    <tbody>
    @foreach($flags as $f)
    <tr>
        <td><code>{{ $f->flag_key }}</code></td>
        <td class="text-muted">{{ $f->description ?? '—' }}</td>
        <td><span class="badge {{ $f->enabled ? 'badge-green' : 'badge-red' }}">{{ $f->enabled ? 'Enabled' : 'Disabled' }}</span></td>
        <td>
            <form method="PUT" action="{{ route('dcc.system.flags.toggle', $f->flag_key) }}">
                @csrf @method('PUT')
                <button type="submit" class="btn btn-sm" style="background:var(--bg3);">Toggle</button>
            </form>
        </td>
    </tr>
    @endforeach
    </tbody>
</table>
</div>
{{ $flags->links() }}
@endsection
