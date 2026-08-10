# Database Architecture

## Overview

Habnut uses MariaDB 10.11+ as its relational store. All tables use the `habnut_` prefix
and `utf8mb4_unicode_ci` collation. Schema is managed by Flyway 9.22+, which runs
automatically on emulator startup.

## Migrations

| Version | Scope |
|---------|-------|
| V1 | Core — users, ranks, rooms, furniture, inventory, friends, messages, groups, badges, achievements, quests, settings, notifications |
| V2 | Games — matches, stats, leaderboards, tournaments |
| V3 | Economy — transactions, catalogue, marketplace, trades, vouchers, memberships, gifts |
| V4 | Audit/Moderation — audit_logs, moderation_actions, reports, chat_logs, bans, appeals, word_filter, mutes |
| V5 | Wired 2.0 — wired_items, wired_variables, wired_chests, wired_execution_log, stack_exports |
| V6 | Roleplay — characters, factions, vehicles, properties, jobs, businesses, licences, crimes, courts, prison, bank, government, elections, medical, dispatch, scenes, crafting, resources |
| V7 | Community Garden — plots, plants, seasons, goals, contributions, harvest_log |
| V8 | Assets/Pets/Bots/Events — asset_registry, pets, bots, events, photos, sound, staff_prefs, feature_flags, system_settings |

## Key Design Principles

### Immutability of Financial Ledgers

`habnut_transactions` and `habnut_rp_bank_transactions` rows are **never updated
or deleted**. Every balance change creates a new row with `balance_before` and
`balance_after`. The `transaction_hash` (SHA-256 of stable fields) and
`idempotency_key` (UUID per operation) together prevent duplicate credits.

### RP Currency Isolation

`habnut_rp_bank_transactions` tracks `rp_cash` only. Classic currencies (`credits`,
`diamonds`, `nut_points`, `seasonal`) exist only in `habnut_transactions`. There is
no foreign-key or application path between the two ledgers — the iron wall is
structural, not just policy.

### Audit Log Retention

`habnut_audit_logs` is immutable. A scheduled job purges rows older than 365 days
(`AUDIT_LOG_RETENTION_DAYS = 365`). All staff and DCC actions must write an audit
row before taking effect.

### FULLTEXT Search

`habnut_chat_logs.message` has a FULLTEXT index for moderator log search.
`habnut_groups.name` has a FULLTEXT index for group search. Both use
`MATCH … AGAINST … IN BOOLEAN MODE`.

### JSON Columns

MariaDB 10.11+ native JSON columns store structured data where the shape is
variable (wired configs, catalogue offer items, pet genetics, quest objectives).
Application code reads these as typed objects; schema validation happens in the
service layer, not at the DB level.

## Connection Pooling

HikariCP 5.1+. Pool configuration is sourced from environment:

| Property | Default |
|----------|---------|
| `DB_POOL_MIN_IDLE` | 5 |
| `DB_POOL_MAX_POOL_SIZE` | 20 |
| `DB_CONNECTION_TIMEOUT_MS` | 3000 |
| `DB_IDLE_TIMEOUT_MS` | 600000 |
| `DB_MAX_LIFETIME_MS` | 1800000 |
| `DB_KEEPALIVE_TIME_MS` | 60000 |

## Important Indexes

- `habnut_rooms (is_public, world_id, rating)` — Navigator popular rooms query
- `habnut_chat_logs (user_id, timestamp)` + `(room_id, timestamp)` — Moderator log lookups
- `habnut_transactions (user_id, idempotency_key)` UNIQUE — Idempotency enforcement
- `habnut_wired_variables (scope, room_id)` — Fast variable lookup during wired execution
- `habnut_leaderboards (game_type, period, total_score DESC)` — Live leaderboard queries
- `habnut_rp_dispatch_calls (status, call_type, created_at)` — Active dispatch board

## Flyway Configuration

Flyway runs in `validateOnMigrate=true` mode. `cleanDisabled=true` prevents
accidental schema wipes in production. `baselineOnMigrate=false` means a fresh
database must have no existing tables.
