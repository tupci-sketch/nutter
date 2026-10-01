<?php

namespace Tests\Feature;

use App\Models\User;
use App\Services\SessionTicketService;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Redis;
use Tests\TestCase;

/**
 * The ticket, against a real Redis.
 *
 * HotelHandoffTest fakes the Redis facade, which proves the service asks for
 * the right key — but the key prefix is applied by the connector, underneath
 * the facade, where a fake cannot see it. That is exactly the mistake this
 * whole handover was broken by: the site wrote `laravel_database_ticket:<T>`
 * holding a serialised PHP integer while the hotel read `habnut:ticket:<T>`
 * expecting `<userId>:<world>`, and nothing failed — the hotel simply found
 * nothing and turned everybody away.
 *
 * So this talks to a real server and reads the key back with a separate client
 * that knows nothing about Laravel, which is the position the hotel is in.
 *
 * Skipped when no Redis is reachable, so the suite still runs on a machine
 * without one. The build has one.
 */
class TicketReachesRedisTest extends TestCase
{
    use RefreshDatabase;

    private const PORT = 6399;

    protected function setUp(): void
    {
        parent::setUp();

        if (! $this->redisIsReachable()) {
            $this->markTestSkipped('No Redis on 127.0.0.1:'.self::PORT.' to test against.');
        }

        // Point the application at the test server, with no prefix configured —
        // the same shape a real install has.
        config([
            'database.redis.client' => 'predis',
            'database.redis.default' => [
                'host' => '127.0.0.1',
                'port' => self::PORT,
                'password' => null,
                'database' => 0,
            ],
        ]);

        Redis::purge('default');
        $this->rawCommand('FLUSHDB');
    }

    protected function tearDown(): void
    {
        if ($this->redisIsReachable()) {
            $this->rawCommand('FLUSHDB');
        }

        parent::tearDown();
    }

    private function redisIsReachable(): bool
    {
        $socket = @fsockopen('127.0.0.1', self::PORT, $errno, $errstr, 1);
        if ($socket === false) {
            return false;
        }
        fclose($socket);

        return true;
    }

    /**
     * Speak to Redis directly, the way the hotel does.
     *
     * Deliberately not through Laravel: a client that shares Laravel's
     * configuration would share its prefix too, and see the key whether or not
     * the hotel could.
     */
    private function rawCommand(string ...$args): ?string
    {
        $socket = fsockopen('127.0.0.1', self::PORT, $errno, $errstr, 2);
        if ($socket === false) {
            $this->fail("Could not reach Redis: {$errstr}");
        }

        $request = '*'.count($args)."\r\n";
        foreach ($args as $arg) {
            $request .= '$'.strlen($arg)."\r\n".$arg."\r\n";
        }
        fwrite($socket, $request);

        $line = fgets($socket);
        $reply = null;

        if ($line !== false) {
            $type = $line[0];
            $rest = rtrim(substr($line, 1), "\r\n");

            if ($type === '$') {
                $length = (int) $rest;
                if ($length >= 0) {
                    $reply = $length === 0 ? '' : fread($socket, $length);
                    fgets($socket);
                }
            } elseif ($type === '+' || $type === ':') {
                $reply = $rest;
            } elseif ($type === '-') {
                fclose($socket);
                $this->fail("Redis said: {$rest}");
            }
        }

        fclose($socket);

        return $reply;
    }

    private function tupci(): User
    {
        return User::factory()->create([
            'username' => 'tupci',
            'email' => 'tupci@icloud.com',
            'password_hash' => Hash::make('testpassword123'),
            'rank' => 7,
            'two_fa_enabled' => false,
            'email_verified_at' => now(),
        ]);
    }

    /** @test */
    public function the_hotel_can_read_the_ticket_the_website_wrote(): void
    {
        $user = $this->tupci();

        $ticket = app(SessionTicketService::class)->issue($user, 'classic');

        // Exactly what the hotel's SessionTicketService.consume does: this key,
        // and nothing else.
        $value = $this->rawCommand('GET', "habnut:ticket:{$ticket}");

        $this->assertNotNull($value,
            'The hotel looks for habnut:ticket:<ticket> and found nothing there. '
            .'Something between the service and the server is changing the key — '
            .'a cache prefix, a Redis prefix, or the wrong connection.');

        $this->assertSame("{$user->id}:classic", $value,
            'The hotel splits this on the first colon into a user id and a world. '
            .'Anything else — a serialised PHP value, a bare integer — and it turns '
            .'the player away.');
    }

    /** @test */
    public function nothing_else_is_written_alongside_it(): void
    {
        $user = $this->tupci();
        $ticket = app(SessionTicketService::class)->issue($user, 'nutropolis');

        $count = (int) $this->rawCommand('DBSIZE');

        $this->assertSame(1, $count,
            'Issuing one ticket wrote more than one key. A prefixed duplicate is how '
            .'this was broken before: the site could see its own key and the hotel '
            .'could not.');

        $this->assertSame("{$user->id}:nutropolis",
            $this->rawCommand('GET', "habnut:ticket:{$ticket}"));
    }

    /** @test */
    public function the_ticket_expires_rather_than_lasting_for_ever(): void
    {
        $user = $this->tupci();
        $ticket = app(SessionTicketService::class)->issue($user, 'classic');

        $ttl = (int) $this->rawCommand('TTL', "habnut:ticket:{$ticket}");

        $this->assertGreaterThan(0, $ttl,
            'A ticket with no expiry is a password that never changes.');
        $this->assertLessThanOrEqual(300, $ttl);
    }

    /** @test */
    public function reading_it_back_through_the_service_agrees_with_the_server(): void
    {
        $user = $this->tupci();
        $tickets = app(SessionTicketService::class);

        $ticket = $tickets->issue($user, 'classic');

        $this->assertSame(
            ['userId' => $user->id, 'world' => 'classic'],
            $tickets->peek($ticket),
        );
    }

    /** @test */
    public function a_revoked_ticket_is_gone_from_the_server(): void
    {
        $user = $this->tupci();
        $tickets = app(SessionTicketService::class);

        $ticket = $tickets->issue($user, 'classic');
        $tickets->revoke($ticket);

        $this->assertNull($this->rawCommand('GET', "habnut:ticket:{$ticket}"));
        $this->assertNull($tickets->peek($ticket));
    }

    /** @test */
    public function clicking_through_to_the_hotel_leaves_a_ticket_the_hotel_can_read(): void
    {
        $user = $this->tupci();

        $response = $this->actingAs($user)->get('/hotel');
        $response->assertRedirect();

        parse_str(parse_url($response->headers->get('Location'), PHP_URL_QUERY) ?? '', $query);

        $this->assertArrayHasKey('ticket', $query);
        $this->assertSame("{$user->id}:classic",
            $this->rawCommand('GET', "habnut:ticket:{$query['ticket']}"),
            'A player clicking Play got a ticket the hotel cannot find.');
    }
}
