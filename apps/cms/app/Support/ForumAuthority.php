<?php

namespace App\Support;

use App\Models\ForumCategory;
use App\Models\ForumModerator;
use App\Models\ForumPost;
use App\Models\ForumThread;
use App\Models\User;
use Illuminate\Support\Facades\DB;

/**
 * Who may do what on the forums.
 *
 * Every rule lives here rather than in the controllers, because the same
 * question gets asked from several places — the board list, a thread page, the
 * moderation queue — and a rule that is written twice eventually disagrees with
 * itself. Views ask the same methods the controllers enforce, so a button is
 * never shown for something the request will then refuse.
 */
class ForumAuthority
{
    /** How long an author may edit their own post before it settles. */
    public const EDIT_WINDOW_MINUTES = 30;

    // ─── reading ────────────────────────────────────────────────────────────

    public function canReadCategory(?User $user, ForumCategory $category): bool
    {
        return $category->readableBy($user);
    }

    /**
     * True when this user may read a thread.
     *
     * A group's forum follows the group's own setting, so a private group's
     * discussions stay private on the website exactly as they do in the hotel.
     */
    public function canReadThread(?User $user, ForumThread $thread): bool
    {
        if ($thread->hidden && ! $this->canModerateThread($user, $thread)) {
            return false;
        }

        if ($thread->isGroupThread()) {
            return $this->canReadGroupForum($user, $thread->group_id);
        }

        return $thread->category !== null && $this->canReadCategory($user, $thread->category);
    }

    /** True when this user may read a group's forum. */
    public function canReadGroupForum(?User $user, int $groupId): bool
    {
        $mode = DB::table('habnut_groups')->where('id', $groupId)->value('forum_mode');

        if ($mode === null || $mode === 'disabled') {
            return false;
        }
        if ($mode === 'open') {
            return true;
        }

        return $this->groupRank($user, $groupId) !== null;
    }

    // ─── writing ────────────────────────────────────────────────────────────

    /** True when this user may start a thread on a board. */
    public function canStartThread(?User $user, ForumCategory $category): bool
    {
        return $this->canSpeak($user) && $category->postableBy($user);
    }

    /**
     * True when this user may reply to a thread.
     *
     * A locked thread takes no replies from anyone but the staff who run the
     * board, which is what locking is for.
     */
    public function canReply(?User $user, ForumThread $thread): bool
    {
        if (! $this->canSpeak($user) || ! $this->canReadThread($user, $thread)) {
            return false;
        }
        if ($thread->locked || $thread->hidden) {
            return $this->canModerateThread($user, $thread);
        }

        if ($thread->isGroupThread()) {
            return $this->canPostInGroupForum($user, $thread->group_id);
        }

        return $thread->category !== null && $thread->category->postableBy($user);
    }

    /** True when this user may post in a group's forum. */
    public function canPostInGroupForum(?User $user, int $groupId): bool
    {
        if (! $this->canReadGroupForum($user, $groupId)) {
            return false;
        }

        $mode = DB::table('habnut_groups')->where('id', $groupId)->value('forum_mode');
        $rank = $this->groupRank($user, $groupId);

        return match ($mode) {
            'open' => $rank !== null,
            'members_only' => in_array($rank, ['owner', 'admin', 'member'], true),
            'admins_only' => in_array($rank, ['owner', 'admin'], true),
            default => false,
        };
    }

    /**
     * True when this user may change a post's text.
     *
     * An author may correct themselves for a short while; after that the thread
     * has been read and quoted, and silently rewriting it would misrepresent the
     * conversation. Forum administrators can still edit, and the post records
     * that they did.
     */
    public function canEditPost(?User $user, ForumPost $post): bool
    {
        if ($user === null || $post->hidden) {
            return false;
        }
        if ($this->isForumAdministrator($user, $post->thread)) {
            return true;
        }
        if ($post->author_id !== $user->id || ! $this->canSpeak($user)) {
            return false;
        }

        return $post->created_at->diffInMinutes(now()) < self::EDIT_WINDOW_MINUTES;
    }

    // ─── moderating ─────────────────────────────────────────────────────────

    /** True when this user may hide, lock or pin within a thread's board. */
    public function canModerateThread(?User $user, ForumThread $thread): bool
    {
        if ($user === null) {
            return false;
        }
        if ($user->isStaff()) {
            return true;
        }
        if ($this->hasForumRole($user, ForumModerator::SCOPE_GLOBAL, null)) {
            return true;
        }

        return $thread->isGroupThread()
            ? $this->moderatesGroup($user, $thread->group_id)
            : $this->hasForumRole($user, ForumModerator::SCOPE_CATEGORY, $thread->category_id);
    }

    /** True when this user may moderate a board. */
    public function canModerateCategory(?User $user, ForumCategory $category): bool
    {
        if ($user === null) {
            return false;
        }

        return $user->isStaff()
            || $this->hasForumRole($user, ForumModerator::SCOPE_GLOBAL, null)
            || $this->hasForumRole($user, ForumModerator::SCOPE_CATEGORY, $category->id);
    }

    /**
     * True when this user holds an administrator role over a thread's board.
     *
     * Administrators do the things that rewrite history — editing somebody
     * else's words, moving a thread to another board — so the role is separate
     * from ordinary moderation.
     */
    public function isForumAdministrator(?User $user, ?ForumThread $thread = null): bool
    {
        if ($user === null) {
            return false;
        }
        if ($user->rank >= User::RANK_ADMIN) {
            return true;
        }
        if ($this->hasForumRole($user, ForumModerator::SCOPE_GLOBAL, null, ForumModerator::ROLE_ADMINISTRATOR)) {
            return true;
        }
        if ($thread === null) {
            return false;
        }

        return $thread->isGroupThread()
            ? $this->hasForumRole($user, ForumModerator::SCOPE_GROUP, $thread->group_id, ForumModerator::ROLE_ADMINISTRATOR)
            : $this->hasForumRole($user, ForumModerator::SCOPE_CATEGORY, $thread->category_id, ForumModerator::ROLE_ADMINISTRATOR);
    }

    /**
     * A group's own owner and admins moderate their forum.
     *
     * Nobody else should have to be appointed to keep order in a group they run.
     */
    private function moderatesGroup(User $user, int $groupId): bool
    {
        if ($this->hasForumRole($user, ForumModerator::SCOPE_GROUP, $groupId)) {
            return true;
        }

        return in_array($this->groupRank($user, $groupId), ['owner', 'admin'], true);
    }

    // ─── shared checks ──────────────────────────────────────────────────────

    /**
     * True when this user is allowed to write anything at all.
     *
     * A player muted in the hotel is muted on the forums too. Letting a mute
     * apply to chat but not to a public board would leave the loudest place on
     * the site wide open to whatever the mute was for.
     */
    public function canSpeak(?User $user): bool
    {
        return $user !== null && ! $this->isMuted($user);
    }

    /** True when an unexpired, unlifted mute covers this user. */
    public function isMuted(User $user): bool
    {
        return DB::table('habnut_mutes')
            ->where('user_id', $user->id)
            ->whereNull('lifted_at')
            ->where('expires_at', '>', now())
            ->exists();
    }

    /** This user's rank within a group, or null when they are not in it. */
    private function groupRank(?User $user, int $groupId): ?string
    {
        if ($user === null) {
            return null;
        }

        $rank = DB::table('habnut_group_members')
            ->where('group_id', $groupId)
            ->where('user_id', $user->id)
            ->value('rank');

        // A pending request or an unanswered invitation is not membership.
        return in_array($rank, ['owner', 'admin', 'member'], true) ? $rank : null;
    }

    /** True when this user holds a forum role in the given scope. */
    private function hasForumRole(User $user, string $scope, ?int $scopeId, ?string $role = null): bool
    {
        $query = ForumModerator::where('user_id', $user->id)->where('scope', $scope);

        $scopeId === null
            ? $query->whereNull('scope_id')
            : $query->where('scope_id', $scopeId);

        if ($role !== null) {
            $query->where('role', $role);
        }

        return $query->exists();
    }
}
