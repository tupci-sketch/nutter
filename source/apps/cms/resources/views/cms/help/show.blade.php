@extends('layouts.app')
@section('title', $article['title'])
@section('content')
<p class="text-muted" style="margin-bottom:0.5rem;font-size:0.85rem;"><a href="{{ route('help.index') }}">← Help</a></p>
<div style="max-width:720px;">
    <h1>{{ $article['title'] }}</h1>
    <div style="line-height:1.8;margin-top:1rem;">{{ $article['body'] }}</div>
</div>
@endsection
