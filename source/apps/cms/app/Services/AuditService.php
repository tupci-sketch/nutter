<?php

namespace App\Services;

use Illuminate\Support\Facades\DB;

class AuditService
{
    public function log(
        int $actorId,
        string $action,
        string $targetType,
        int $targetId,
        array $metadata = [],
        ?string $ip = null,
        ?string $roomContext = null,
    ): void {
        DB::table('habnut_audit_logs')->insert([
            'actor_user_id' => $actorId,
            'action' => $action,
            'target_type' => $targetType,
            'target_id' => $targetId,
            'target_user_id' => $targetType === 'user' ? $targetId : null,
            'metadata' => json_encode($metadata),
            'ip_address' => $ip ?? request()->ip(),
            'room_context' => $roomContext,
            'source' => 'dcc',
            'created_at' => now(),
        ]);
    }
}
