<?php

namespace App\Services;

use App\Models\User;
use Illuminate\Support\Facades\Redis;

/**
 * The handover between the website and the hotel.
 *
 * A player signs in once, here. When they open the hotel the site hands the
 * game client a short-lived ticket, the client presents it over the socket,
 * and the hotel looks it up and knows who just arrived. The player never types
 * a password into the game, and never has two accounts to keep in step.
 *
 * The ticket is written straight to Redis rather than through the cache,
 * because the hotel reads the key itself: the cache would add its own prefix
 * and store a serialised PHP value, and the hotel would find nothing it could
 * read. The shape below — `habnut:ticket:<ticket>` holding `<userId>:<worldId>`
 * — is the shape the hotel's own SessionTicketService writes and consumes, so
 * a ticket from either side is indistinguishable from the other's.
 */
class SessionTicketService
{
    /** The hotel's key prefix. Changing this breaks sign-in; change both. */
    public const KEY_PREFIX = 'habnut:ticket:';

    /** The worlds a ticket may be issued for. */
    public const WORLDS = ['classic', 'nutropolis'];

    /**
     * Issue a ticket for this user and world, and return it.
     *
     * The ticket is single-use: the hotel deletes the key as it reads it, so a
     * ticket that leaks into a URL bar or a log is worthless the moment the
     * player has used it, and worthless anyway once the TTL passes.
     */
    public function issue(User|int $user, string $world = 'classic'): string
    {
        $userId = $user instanceof User ? $user->id : $user;
        $world = $this->normaliseWorld($world);

        $ticket = $this->generate();

        Redis::setex(
            self::KEY_PREFIX.$ticket,
            $this->ttlSeconds(),
            $userId.':'.$world,
        );

        return $ticket;
    }

    /**
     * Read a ticket back without consuming it.
     *
     * Only the hotel consumes tickets. This exists so the site can show an
     * admin that the ticket they just issued is live, and so the tests can
     * check what was actually written.
     *
     * @return array{userId:int,world:string}|null
     */
    public function peek(string $ticket): ?array
    {
        $value = Redis::get(self::KEY_PREFIX.$ticket);

        if (! is_string($value) || ! str_contains($value, ':')) {
            return null;
        }

        [$userId, $world] = explode(':', $value, 2);

        return ['userId' => (int) $userId, 'world' => $world];
    }

    /** Throw a ticket away — used when a session is ended before it is spent. */
    public function revoke(string $ticket): void
    {
        Redis::del(self::KEY_PREFIX.$ticket);
    }

    /** Seconds a ticket stays good for. Short on purpose. */
    public function ttlSeconds(): int
    {
        return max(30, (int) config('habnut.ticket_ttl_minutes', 5) * 60);
    }

    /**
     * An unknown or absent world falls back to this hotel's own, not failing.
     *
     * Which world that is comes from configuration, because a stack running the
     * roleplay city has to issue tickets for the city: the world travels on the
     * ticket, so a hard-coded fallback here would run the city and put everybody
     * in the hotel.
     */
    public function normaliseWorld(?string $world): string
    {
        $world = strtolower(trim((string) $world));

        if (in_array($world, self::WORLDS, true)) {
            return $world;
        }

        return $this->defaultWorld();
    }

    /** The world this hotel puts a player in when they do not ask. */
    public function defaultWorld(): string
    {
        $configured = strtolower(trim((string) config('habnut.default_world', 'classic')));

        return in_array($configured, self::WORLDS, true) ? $configured : self::WORLDS[0];
    }

    /**
     * 32 random bytes, URL-safe, unpadded.
     *
     * The same shape the hotel generates, so neither side can tell which end
     * of the handover made a given ticket.
     */
    private function generate(): string
    {
        return rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '=');
    }
}
