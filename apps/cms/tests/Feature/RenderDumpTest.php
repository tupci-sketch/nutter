<?php
namespace Tests\Feature;
use App\Models\User;
use App\Models\NewsArticle;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class RenderDumpTest extends TestCase
{
    use RefreshDatabase;

    public function test_dump(): void
    {
        $tupci = User::factory()->create([
            'username' => 'tupci', 'motto' => 'Chief acorn architect',
            'credits' => 12500, 'diamonds' => 42, 'nut_points' => 380,
            'achievement_score' => 1275, 'rank' => User::RANK_ADMIN, 'online' => true,
        ]);
        User::factory()->create(['username'=>'nutmod','rank'=>User::RANK_MODERATOR,'online'=>true,'achievement_score'=>820]);
        User::factory()->create(['username'=>'acornhelper','rank'=>User::RANK_HELPER,'online'=>true,'achievement_score'=>640]);
        User::factory()->count(6)->create(['online'=>true,'achievement_score'=>300]);

        foreach ([
            ['Winter Festival opens in the Plaza','winter-festival','The Plaza has been redecorated and the seasonal shop is stocked.'],
            ['Wired 2.0 debugger now live','wired-debugger','Watch your stacks execute step by step from inside the room.'],
            ['Nutropolis: territory update','nutropolis-turf','Factions can now contest turf. Holding one pays hourly.'],
        ] as [$t,$s,$e]) {
            NewsArticle::create(['title'=>$t,'slug'=>$s,'excerpt'=>$e,'body'=>'...','published_at'=>now()->subDays(rand(1,9)),'author_id'=>$tupci->id]);
        }

        file_put_contents('/tmp/home_guest.html', $this->get('/')->getContent());
        file_put_contents('/tmp/home_me.html', $this->actingAs($tupci)->get('/')->getContent());
        $this->assertTrue(true);
    }
}
