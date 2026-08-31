<?php

namespace App\Http\Controllers\Dcc;

use App\Http\Controllers\Controller;
use App\Services\AuditService;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class DccGroupsController extends Controller
{
    public function __construct(private AuditService $audit) {}

    public function index(Request $request)
    {
        $query = DB::table('habnut_groups')
            ->join('users', 'users.id', '=', 'habnut_groups.owner_id')
            ->select('habnut_groups.*', 'users.username as owner_name');

        if ($request->filled('q')) {
            $query->where('habnut_groups.name', 'like', '%'.$request->q.'%');
        }

        $groups = $query->orderByDesc('habnut_groups.created_at')->paginate(25);

        return view('dcc.groups.index', compact('groups'));
    }

    public function show(int $id)
    {
        $group = DB::table('habnut_groups')->where('id', $id)->firstOrFail();
        $members = DB::table('habnut_group_members')
            ->join('users', 'users.id', '=', 'habnut_group_members.user_id')
            ->where('group_id', $id)
            ->select('habnut_group_members.*', 'users.username')
            ->get();

        return view('dcc.groups.show', compact('group', 'members'));
    }

    public function destroy(Request $request, int $id)
    {
        DB::table('habnut_group_members')->where('group_id', $id)->delete();
        DB::table('habnut_groups')->where('id', $id)->delete();
        $this->audit->log($request->user()->id, 'group_delete', 'group', $id, []);

        return redirect()->route('dcc.groups.index')->with('success', 'Group deleted.');
    }

    public function verify(Request $request, int $id)
    {
        DB::table('habnut_groups')->where('id', $id)->update(['verified' => true]);
        $this->audit->log($request->user()->id, 'group_verify', 'group', $id, []);

        return back()->with('success', 'Group verified.');
    }
}
