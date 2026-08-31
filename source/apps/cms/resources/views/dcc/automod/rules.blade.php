@extends('layouts.dcc')
@section('title','Content rules')
@section('content')
<h1>Content rules</h1>

<p class="text-muted" style="font-size:0.86rem;max-width:680px;">
    These describe the small set of things that are harmful whoever is reading. Swearing and
    adult conversation are deliberately not here, and adding them would change what this hotel
    is. Patterns are matched against a tidied-up form of each message: accents stripped, digits
    read as the letters they stand in for, and long runs of a letter collapsed — so write
    <code>kil+</code> rather than <code>kill</code>. Use <code>[0-9]</code> for a digit,
    <code>[ ]</code> for a space and <code>(?&lt;![a-z])</code> for a word edge; a backslash
    means different things to different databases and is refused.
</p>

<div class="card" style="padding:0;margin-top:1rem;"><table>
<thead><tr><th>Rule</th><th>Pattern</th><th>Match</th><th>Does</th><th>Severity</th><th>Mute</th><th></th></tr></thead>
<tbody>
@foreach($rules as $rule)
<tr>
    <td>
        <span class="badge {{ in_array($rule->category, ['minor_safety','hate']) ? 'badge-red' : 'badge-amber' }}">
            {{ str_replace('_', ' ', $rule->category) }}
        </span><br>
        {{ $rule->label }}
    </td>
    <td style="max-width:280px;"><code style="font-size:0.75rem;overflow-wrap:anywhere;">{{ $rule->pattern }}</code></td>
    <td class="text-muted">{{ $rule->match_mode }}</td>
    <td><span class="badge {{ $rule->action === 'mute' ? 'badge-red' : 'badge-amber' }}">{{ $rule->action }}</span></td>
    <td>{{ $rule->severity }}</td>
    <td class="text-muted">{{ $rule->mute_minutes }}m</td>
    <td>
        <form method="POST" action="{{ route('dcc.automod.rule.toggle', $rule->id) }}">
            @csrf
            <button class="btn btn-sm {{ $rule->enabled ? '' : 'btn-primary' }}" type="submit">
                {{ $rule->enabled ? 'Switch off' : 'Switch on' }}
            </button>
        </form>
    </td>
</tr>
@endforeach
</tbody></table></div>

<h2 style="margin-top:1.5rem;">New rule</h2>
<div class="card">
    <form method="POST" action="{{ route('dcc.automod.rule.store') }}"
          style="display:grid;gap:0.5rem;max-width:520px;">
        @csrf
        <label>Category
            <select name="category">
                @foreach($categories as $category)
                    <option value="{{ $category }}">{{ str_replace('_', ' ', $category) }}</option>
                @endforeach
            </select>
        </label>
        <label>What it catches <input name="label" required maxlength="128" value="{{ old('label') }}"></label>
        <label>Pattern <input name="pattern" required maxlength="512" value="{{ old('pattern') }}"></label>
        <label>Exemption, if any <input name="exempt_pattern" maxlength="512" value="{{ old('exempt_pattern') }}"></label>
        <label>Match against
            <select name="match_mode">
                <option value="words">The message as written</option>
                <option value="condensed">The message with spacing removed</option>
                <option value="both">Both</option>
            </select>
        </label>
        <label>Does
            <select name="action">
                <option value="mute">Mute and raise a case</option>
                <option value="flag">Raise a case only</option>
            </select>
        </label>
        <label>Severity, 1 to 5 <input name="severity" type="number" min="1" max="5" value="{{ old('severity', 3) }}" required></label>
        <label>Fallback mute, minutes <input name="mute_minutes" type="number" min="5" max="20160" value="{{ old('mute_minutes', 1440) }}" required></label>
        <button class="btn btn-primary" type="submit">Add rule</button>
    </form>
</div>
@endsection
