# Runbook: EconomyIdempotencyViolation (ALT-022)

**Severity**: Critical  
**Alert**: `EconomyIdempotencyViolation` — `rate(habnut_economy_duplicate_idempotency_total[5m]) > 0` for 1 minute

## What This Means

An idempotency key collision was detected — two transaction attempts with the same
`idempotency_key` were received. The second was rejected as a duplicate. This alert
firing means the duplicate-rejection mechanism is working, but the underlying cause
must be investigated immediately to rule out a double-spend bug or a client bug.

## Investigation

```bash
# 1. Check recent audit log for duplicate idempotency events
mysql -u habnut -p habnut -e "
  SELECT actor_user_id, action, metadata, created_at
  FROM habnut_audit_logs
  WHERE action = 'economy.idempotency_violation'
  ORDER BY created_at DESC
  LIMIT 20;"

# 2. Check transactions table for suspicious patterns
mysql -u habnut -p habnut -e "
  SELECT user_id, idempotency_key, COUNT(*) as attempts, MIN(created_at), MAX(created_at)
  FROM habnut_transactions
  GROUP BY user_id, idempotency_key
  HAVING COUNT(*) > 1
  ORDER BY MAX(created_at) DESC
  LIMIT 10;"

# 3. Check Prometheus metric for trend
# Visit Grafana → Economy dashboard → Idempotency Violations panel
```

## Root Cause Identification

### Client retry storm
If the same key appears from the same user within seconds, a client retry loop is
generating duplicate keys. Check the client version and rate limiter logs.

### Server-side retry bug
If keys appear from the emulator's internal services, a service is retrying an
already-completed operation. Check `EconomyService.java` for missing idempotency
guards.

### Key collision (hash collision)
Extremely unlikely with UUID v4 keys; treat as a bug if confirmed.

## Resolution

1. Identify which user(s) and transaction type(s) are affected from the audit log.
2. Verify their balance is correct (compare `balance_after` of the most recent
   successful transaction against their current `credits`/`diamonds` column).
3. If balance is wrong, manually insert a corrective transaction row with a new
   idempotency key — never UPDATE an existing row.
4. File a bug report with the specific idempotency keys and user IDs.

## Never
- NEVER update or delete rows in `habnut_transactions`.
- NEVER silence this alert without root-cause identification.
