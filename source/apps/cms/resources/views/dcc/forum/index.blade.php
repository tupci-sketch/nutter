@extends('layouts.dcc')
@section('title','Forums')
@section('content')
<h1>Forums</h1>

@if($openReports > 0)
    <p class="alert alert-error">
        {{ $openReports }} reported {{ Str::plural('post', $openReports) }} waiting.
        <a href="{{ route('dcc.forum.reports') }}">Open the queue</a>.
    </p>
@endif

<h2 style="margin-top:1.2rem;">Boards</h2>
<div class="card" style="padding:0;"><table>
<thead><tr><th>Board</th><th>Slug</th><th>Read</th><th>Post</th><th>Order</th><th>Threads</th><th>State</th><th></th></tr></thead>
<tbody>
@foreach($categories as $c)
<tr>
    <td>{{ $c->name }}<br><span class="text-muted" style="font-size:0.78rem;">{{ $c->description }}</span></td>
    <td class="text-muted">{{ $c->slug }}</td>
    <td>rank {{ $c->min_read_rank }}</td>
    <td>rank {{ $c->min_post_rank }}</td>
    <td>{{ $c->sort_order }}</td>
    <td>{{ number_format($c->thread_count) }}</td>
    <td><span class="badge {{ $c->locked ? 'badge-red' : 'badge-green' }}">{{ $c->locked ? 'Archived' : 'Open' }}</span></td>
    <td>
        <details>
            <summary class="btn btn-sm" style="background:var(--bg3);list-style:none;">Edit</summary>
            <form method="POST" action="{{ route('dcc.forum.category.update', $c->id) }}"
                  style="margin-top:0.5rem;display:grid;gap:0.4rem;min-width:260px;">
                @csrf @method('PUT')
                <label>Name <input name="name" value="{{ $c->name }}" required></label>
                <label>Description <input name="description" value="{{ $c->description }}"></label>
                <label>Minimum rank to read <input name="min_read_rank" type="number" min="0" max="7" value="{{ $c->min_read_rank }}" required></label>
                <label>Minimum rank to post <input name="min_post_rank" type="number" min="0" max="7" value="{{ $c->min_post_rank }}" required></label>
                <label>Sort order <input name="sort_order" type="number" min="0" max="9999" value="{{ $c->sort_order }}" required></label>
                <label><input type="checkbox" name="locked" value="1" @checked($c->locked) style="width:auto;"> Archived</label>
                <button class="btn btn-sm btn-primary" type="submit">Save</button>
            </form>
        </details>
    </td>
</tr>
@endforeach
</tbody></table></div>

<h2 style="margin-top:1.5rem;">New board</h2>
<div class="card">
    <form method="POST" action="{{ route('dcc.forum.category.store') }}"
          style="display:grid;gap:0.5rem;max-width:420px;">
        @csrf
        <label>Name <input name="name" required value="{{ old('name') }}"></label>
        <label>Slug <input name="slug" required pattern="[a-z0-9-]+" value="{{ old('slug') }}"></label>
        <label>Description <input name="description" value="{{ old('description') }}"></label>
        <label>Minimum rank to read <input name="min_read_rank" type="number" min="0" max="7" value="{{ old('min_read_rank', 0) }}" required></label>
        <label>Minimum rank to post <input name="min_post_rank" type="number" min="0" max="7" value="{{ old('min_post_rank', 1) }}" required></label>
        <label>Sort order <input name="sort_order" type="number" min="0" max="9999" value="{{ old('sort_order', 100) }}" required></label>
        <button class="btn btn-primary" type="submit">Create board</button>
    </form>
    @error('slug')<p class="alert alert-error">{{ $message }}</p>@enderror
</div>

<h2 style="margin-top:1.5rem;">Forum staff</h2>
<p class="text-muted" style="font-size:0.85rem;margin-bottom:0.6rem;">
    Forum roles are separate from hotel rank, so a player can be trusted with a board without
    being given moderator powers inside the hotel.
</p>
<div class="card" style="padding:0;"><table>
<thead><tr><th>Player</th><th>Scope</th><th>Role</th><th>Since</th><th></th></tr></thead>
<tbody>
@forelse($moderators as $m)
<tr>
    <td>{{ $m->user?->username ?? 'unknown' }}</td>
    <td>{{ $m->scope }}{{ $m->scope_id ? ' #'.$m->scope_id : '' }}</td>
    <td>{{ $m->role }}</td>
    <td class="text-muted">{{ $m->granted_at?->format('Y-m-d') }}</td>
    <td>
        <form method="POST" action="{{ route('dcc.forum.role.revoke', $m->id) }}">
            @csrf @method('DELETE')
            <button class="btn btn-sm btn-danger" type="submit">Remove</button>
        </form>
    </td>
</tr>
@empty
<tr><td colspan="5" class="text-muted">Nobody holds a forum role yet.</td></tr>
@endforelse
</tbody></table></div>

<div class="card" style="margin-top:0.8rem;">
    <form method="POST" action="{{ route('dcc.forum.role.grant') }}"
          style="display:grid;gap:0.5rem;max-width:420px;">
        @csrf
        <label>Username <input name="username" required value="{{ old('username') }}"></label>
        <label>Scope
            <select name="scope">
                <option value="category">One board</option>
                <option value="group">One group</option>
                <option value="global">Every board</option>
            </select>
        </label>
        <label>Board or group id <input name="scope_id" type="number" min="1" value="{{ old('scope_id') }}"></label>
        <label>Role
            <select name="role">
                <option value="moderator">Moderator — hide, lock, pin</option>
                <option value="administrator">Administrator — also edit and move</option>
            </select>
        </label>
        <button class="btn btn-primary" type="submit">Grant role</button>
    </form>
    @error('scope_id')<p class="alert alert-error">{{ $message }}</p>@enderror
</div>
@endsection
