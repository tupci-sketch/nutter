# Acceptance Criteria

All criteria must be satisfied before the platform is considered shippable.

## Functional Acceptance

### Auth and Sessions (AUT)
- [x] Players can register with username/email/password
- [x] Email verification gate before login
- [x] Secure login with session ticket issuance (Redis, 5-min TTL)
- [x] 2FA enrolment and verification (TOTP)
- [x] Active bans enforced at login
- [x] Machine ID captured on login
- [x] World switching (Classic ↔ Nutropolis)
- [x] Session expiry and graceful disconnect

### Rooms (ROM, NAV)
- [x] Room creation, loading, and unloading
- [x] A* pathfinding and movement validation
- [x] Access modes: open, doorbell, password, invisible
- [x] Rights, bans, kicks, mutes (room-scoped)
- [x] Furniture placement, movement, rotation, pickup
- [x] Stacking, rollers, teleport linking
- [x] Navigator: search, categories, popular, my rooms
- [x] Room ratings, favourites, visit counts

### Economy (ECO, CAT, INV, TRD, MKT, PRO)
- [x] Credits, Diamonds, Nut Points, Seasonal currencies
- [x] `habnut_transactions` INSERT-only, idempotency_key enforced
- [x] Catalogue: pages, items, limited editions, gifts, vouchers
- [x] Inventory: list, search, sort
- [x] Trading: full state machine, atomic commit
- [x] Marketplace: list, buy, expire, price history
- [x] Membership tiers with perks
- [x] RP currency (RP Cash + Bank) NEVER touches classic economy tables

### Social (FRI, MSG, GRP)
- [x] Friend requests, acceptance, blocking, online/offline delivery
- [x] Groups: creation, membership, forum (threads/posts/pin/lock/hide)

### Progression (BDG, ACH, QST, PRF)
- [x] Achievements tracked, completed exactly once, badge granted
- [x] Daily/weekly/seasonal quests with rotation
- [x] Level system with XP and unlocks
- [x] Full profile page (look, motto, badges, stats)

### Wired 2.0 (Wired)
- [x] All 21 triggers implemented
- [x] All 55 actions implemented
- [x] All 37 conditions implemented
- [x] All 22 selectors implemented
- [x] Variables: room/user/global scope, number/text/bool
- [x] Signals: room-local and rate-limited global
- [x] Full operator set (arithmetic, text, conversion)
- [x] Debugger: live stream, variable inspector, step mode
- [x] Conformance suite: 100% pass

### Games (Game Engine, TRN)
- [x] Football (FootballMatch + Ball)
- [x] Battleball, Freeze, Racing, Telephrase
- [x] Wired-constructible game frameworks
- [x] Tournament system with brackets and matchmaking
- [x] 10 tick/sec game loop isolated from Wired tick
- [x] Stats recorded to leaderboards

### Pets, Bots, Camera, Sound
- [x] Pet AI, commands, training, genetics, breeding
- [x] Bot placement, chat modes, walk modes, Wired integration
- [x] Camera: capture, store, purchase, display
- [x] Sound machine, playlist editing, playback sync

### Moderation and Staff (MOD, STF)
- [x] Report queue: create, assign, resolve
- [x] Mute/kick/ban/warn with reversals, global and room-scoped
- [x] Chat log search (FULLTEXT)
- [x] Appeals system
- [x] Staff overlay with 8 tools
- [x] ~25 built-in commands
- [x] Word filter with severity levels
- [x] Audit log: immutable, 365-day retention

### Community Garden (GRD)
- [x] Plot management, planting, watering, withering, harvest
- [x] Community goal aggregation
- [x] Seasonal plant rotation
- [x] Garden milestone badges

### Nutropolis RP World
- [x] Characters, factions, jobs/shifts/payroll
- [x] Vehicles, properties, businesses, licences
- [x] Crimes, criminal records, courts, prison
- [x] Government, elections
- [x] Medical, dispatch, RP scenes
- [x] RP bank accounts isolated from Classic currency
- [x] Crafting recipes and resources

### CMS and DCC
- [x] Public: registration, login, 2FA, password reset, profile, hotel view, news, help, privacy, terms
- [x] DCC: all 45 admin areas
- [x] All DCC actions audit-logged
- [x] Laravel Horizon queue monitoring

## Technical Acceptance

### Security
- [x] TLS 1.2/1.3 only, HSTS preload
- [x] Full CSP, X-Frame-Options DENY, X-Content-Type-Options
- [x] Rate limiting enforced server-side (5/s chat, 10/s movement, 100/s combined)
- [x] Server-authoritative: all match scores, Wired RNG, economy
- [x] No "Habbo" strings in any player-facing context
- [x] Economy immutability: no UPDATE/DELETE on habnut_transactions

### Observability
- [x] 27 Prometheus alert rules (ALT-001–ALT-027)
- [x] 15 Grafana dashboards
- [x] Structured JSON logging via Loki
- [x] JVM metrics via Micrometer

### CI
- [x] 15-step GitHub Actions pipeline
- [x] Placeholder scan gates build
- [x] Asset validation in CI
- [x] Wired conformance 100% required
- [x] Coverage gates enforced

### Deployment
- [x] `habnutctl install` (28 steps) provisions from scratch
- [x] `habnutctl update` (13 steps) with automatic rollback on failure
- [x] `habnutctl doctor` reports healthy on fresh install
- [x] Docker Compose stack fully defined with health checks and resource limits
- [x] Flyway migrations V1–V8 apply cleanly in order
