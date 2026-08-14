<?php

namespace App\Http\Controllers\Dcc;

use App\Http\Controllers\Controller;
use App\Services\AuditService;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class DccRoomsController extends Controller
{
    public function __construct(private AuditService $audit) {}

    public function index(Request $request)
    {
        $query = DB::table('habnut_rooms')
            ->join('users', 'users.id', '=', 'habnut_rooms.owner_id')
            ->select('habnut_rooms.*', 'users.username as owner_name');

        if ($request->filled('q')) {
            $query->where('habnut_rooms.name', 'like', '%' . $request->q . '%');
        }

        $rooms = $query->orderByDesc('habnut_rooms.visitors_now')->paginate(25);

        return view('dcc.rooms.index', compact('rooms'));
    }

    public function show(int $id)
    {
        $room = DB::table('habnut_rooms')
            ->join('users', 'users.id', '=', 'habnut_rooms.owner_id')
            ->select('habnut_rooms.*', 'users.username as owner_name')
            ->where('habnut_rooms.id', $id)
            ->firstOrFail();

        return view('dcc.rooms.show', compact('room'));
    }

    public function destroy(Request $request, int $id)
    {
        DB::table('habnut_rooms')->where('id', $id)->delete();
        $this->audit->log($request->user()->id, 'room_delete', 'room', $id, []);
        return redirect()->route('dcc.rooms.index')->with('success', 'Room deleted.');
    }

    public function toggleFeatured(Request $request, int $id)
    {
        $room = DB::table('habnut_rooms')->where('id', $id)->first();
        $featured = !$room->featured;
        DB::table('habnut_rooms')->where('id', $id)->update(['featured' => $featured]);
        $this->audit->log($request->user()->id, 'room_feature_toggle', 'room', $id, ['featured' => $featured]);
        return back()->with('success', $featured ? 'Room featured.' : 'Room unfeatured.');
    }
}
