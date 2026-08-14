<?php

namespace Tests\Unit;

use Tests\TestCase;
use App\Services\AuditService;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;

class AuditServiceTest extends TestCase
{
    use RefreshDatabase;

    private AuditService $audit;
    private User $tupci;

    protected function setUp(): void
    {
        parent::setUp();
        $this->audit = new AuditService();
        $this->tupci = User::factory()->create([
            'username' => 'tupci',
            'email'    => 'tupci@icloud.com',
            'rank'     => 7,
        ]);
    }

    /** @test */
    public function audit_log_inserts_a_row(): void
    {
        $this->audit->log(
            actorId:    $this->tupci->id,
            action:     'rank_change',
            targetType: 'user',
            targetId:   2,
            metadata:   ['old' => 1, 'new' => 3],
        );

        $this->assertDatabaseHas('habnut_audit_logs', [
            'actor_user_id' => $this->tupci->id,
            'action'        => 'rank_change',
            'target_type'   => 'user',
            'target_id'     => 2,
            'source'        => 'dcc',
        ]);
    }

    /** @test */
    public function audit_log_sets_target_user_id_when_target_is_user(): void
    {
        $this->audit->log($this->tupci->id, 'ban', 'user', 99);

        $row = DB::table('habnut_audit_logs')
            ->where('action', 'ban')
            ->where('target_type', 'user')
            ->first();

        $this->assertEquals(99, $row->target_user_id);
    }

    /** @test */
    public function audit_log_leaves_target_user_id_null_for_non_user_targets(): void
    {
        $this->audit->log($this->tupci->id, 'room_close', 'room', 55);

        $row = DB::table('habnut_audit_logs')
            ->where('action', 'room_close')
            ->first();

        $this->assertNull($row->target_user_id);
    }

    /** @test */
    public function audit_log_metadata_is_json_encoded(): void
    {
        $meta = ['old_rank' => 1, 'new_rank' => 5, 'reason' => 'promotion'];

        $this->audit->log($this->tupci->id, 'rank_change', 'user', 5, $meta);

        $row = DB::table('habnut_audit_logs')
            ->where('action', 'rank_change')
            ->where('target_id', 5)
            ->first();

        $decoded = json_decode($row->metadata, true);
        $this->assertEquals($meta, $decoded);
    }

    /** @test */
    public function audit_log_is_immutable_insert_only(): void
    {
        $this->audit->log($this->tupci->id, 'credits_grant', 'user', 10, ['amount' => 100]);

        // Verify only one row was created (no UPDATE path exists in AuditService).
        $count = DB::table('habnut_audit_logs')
            ->where('actor_user_id', $this->tupci->id)
            ->where('action', 'credits_grant')
            ->count();

        $this->assertEquals(1, $count);
    }

    /** @test */
    public function multiple_audit_events_accumulate(): void
    {
        $this->audit->log($this->tupci->id, 'ban', 'user', 10);
        $this->audit->log($this->tupci->id, 'ban', 'user', 11);
        $this->audit->log($this->tupci->id, 'ban', 'user', 12);

        $count = DB::table('habnut_audit_logs')
            ->where('actor_user_id', $this->tupci->id)
            ->where('action', 'ban')
            ->count();

        $this->assertEquals(3, $count);
    }
}
