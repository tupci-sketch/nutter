@extends('layouts.dcc')
@section('title', 'Economy Stats')
@section('content')
<h1>Economy Stats (30 days)</h1>
<div class="card">
    <table>
        <thead><tr><th>Date</th><th>Currency</th><th>Volume</th></tr></thead>
        <tbody>
        @foreach($daily as $row)
        <tr>
            <td>{{ $row->d }}</td>
            <td>{{ $row->currency }}</td>
            <td>{{ number_format($row->vol) }}</td>
        </tr>
        @endforeach
        </tbody>
    </table>
</div>
@endsection
