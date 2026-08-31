@extends('layouts.dcc')
@section('title', 'Word Filter')
@section('content')
<h1>Word Filter</h1>
<div style="display:grid;grid-template-columns:1fr 1fr;gap:1.25rem;">
<div class="card">
    <h2>Add Word</h2>
    <form method="POST" action="{{ route('dcc.moderation.wordfilter.add') }}" style="margin-top:0.75rem;">
        @csrf
        <div class="form-group"><label>Word / phrase</label><input type="text" name="word" required></div>
        <div class="form-group"><label>Replacement</label><input type="text" name="replacement" placeholder="****"></div>
        <div class="form-group"><label>Severity</label>
            <select name="severity"><option value="low">Low</option><option value="medium">Medium</option><option value="high">High</option></select>
        </div>
        <button type="submit" class="btn btn-primary btn-sm">Add</button>
    </form>
</div>
<div class="card" style="padding:0;overflow:auto;">
    <table>
        <thead><tr><th>Word</th><th>Replacement</th><th>Severity</th><th></th></tr></thead>
        <tbody>
        @foreach($words as $w)
        <tr>
            <td><code>{{ $w->word }}</code></td>
            <td>{{ $w->replacement }}</td>
            <td><span class="badge {{ $w->severity==='high'?'badge-red':($w->severity==='medium'?'badge-warn':'badge-green') }}">{{ $w->severity }}</span></td>
            <td>
                <form method="POST" action="{{ route('dcc.moderation.wordfilter.remove', $w->id) }}">
                    @csrf @method('DELETE')
                    <button type="submit" class="btn btn-sm btn-danger">Remove</button>
                </form>
            </td>
        </tr>
        @endforeach
        </tbody>
    </table>
</div>
</div>
{{ $words->links() }}
@endsection
