<?php

namespace App\Http\Controllers\Cms;

use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\Rule;

class ProfileController extends Controller
{
    public function show(Request $request)
    {
        return view('cms.profile.show', ['user' => $request->user()]);
    }

    public function update(Request $request)
    {
        $user = $request->user();

        $request->validate([
            'email' => ['required', 'email', Rule::unique('users')->ignore($user->id)],
            'password' => ['nullable', 'confirmed', 'min:8'],
        ]);

        $data = ['email' => $request->email];
        if ($request->filled('password')) {
            $data['password'] = Hash::make($request->password);
        }

        $user->update($data);

        return back()->with('success', 'Profile updated.');
    }

    public function updateMotto(Request $request)
    {
        $request->validate(['motto' => ['required', 'string', 'max:255']]);
        $request->user()->update(['motto' => $request->motto]);

        return back()->with('success', 'Motto updated.');
    }

    /**
     * A player's public profile.
     *
     * Everything shown here is public in the hotel itself: the figure, the
     * badges the player chose to display, and how much of the hotel they have
     * taken part in. Nothing private to the account is read.
     */
    public function view(string $username)
    {
        $user = User::where('username', $username)->firstOrFail();

        return view('cms.profile.view', [
            'user' => $user,
            'badges' => $this->equippedBadges($user),
            'stats' => $this->publicStats($user),
            'groups' => $this->groups($user),
            'rooms' => $this->rooms($user),
        ]);
    }

    /**
     * The badges a player has chosen to show, in their chosen order.
     *
     * A player picks five slots in the client; the profile shows the same five
     * so the page agrees with what other players see in the hotel.
     */
    private function equippedBadges(User $user): Collection
    {
        return DB::table('habnut_user_badges as ub')
            ->leftJoin('habnut_badges as b', 'b.code', '=', 'ub.badge_code')
            ->where('ub.user_id', $user->id)
            ->where('ub.equipped', 1)
            ->orderBy('ub.slot_index')
            ->limit(5)
            ->get(['ub.badge_code', 'b.name', 'b.description']);
    }

    /** Counts that describe how much of the hotel a player has seen. */
    private function publicStats(User $user): array
    {
        return [
            'friends' => DB::table('habnut_friends')
                ->where('accepted', 1)
                ->where(fn ($q) => $q->where('user_a', $user->id)->orWhere('user_b', $user->id))
                ->count(),
            'badges' => DB::table('habnut_user_badges')->where('user_id', $user->id)->count(),
            'achievements' => DB::table('habnut_user_achievements')
                ->where('user_id', $user->id)
                ->whereNotNull('completed_at')
                ->count(),
            'rooms' => DB::table('habnut_rooms')->where('owner_id', $user->id)->count(),
        ];
    }

    /** Groups the player belongs to, owned ones first. */
    private function groups(User $user): Collection
    {
        return DB::table('habnut_group_members as gm')
            ->join('habnut_groups as g', 'g.id', '=', 'gm.group_id')
            ->where('gm.user_id', $user->id)
            ->whereIn('gm.rank', ['owner', 'admin', 'member'])
            ->orderByRaw("FIELD(gm.`rank`, 'owner', 'admin', 'member')")
            ->limit(6)
            ->get(['g.id', 'g.name', 'g.badge', 'gm.rank']);
    }

    /** The player's own rooms, busiest first. */
    private function rooms(User $user): Collection
    {
        return DB::table('habnut_rooms')
            ->where('owner_id', $user->id)
            ->orderByDesc('user_count')
            ->limit(6)
            ->get(['id', 'name', 'description', 'user_count', 'max_users']);
    }
}
