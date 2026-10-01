# Habnut Completion Ledger

Each row represents a feature area. All 10 completion dimensions must be green
before a row is marked complete. Dimensions: **DB** · **SVC** · **PKT** · **UI** · **DCC** · **PERM** · **TEST** · **METRIC** · **ASSET** · **DOC**

## Verification status

The per-feature table below records intended scope. The build state that has
actually been measured is recorded here, and two dimensions do not yet meet the
bar the table claims.

Verified green:

| Check | Result |
|-------|--------|
| Emulator unit tests | 214 passing |
| Wired 2.0 conformance | 254 passing, the Chapter 59.1 gate met |
| Wired 2.0 registry | 21 triggers · 56 actions · 37 conditions · 22 selectors |
| Client tests | 120 passing; typecheck and lint clean |
| CMS tests | 123 passing |
| Launcher tests | imager, extractor, payload, seed, dev and installer suites |
| Coverage floors | enforced per module, build fails below them |
| Database migrations | V1–V17, applied in order against H2 by the schema guard |
| Schema guard | every column the services select, insert into, or name bare, checked against the migrations |
| Protocol | the client's packet table generated from the server's, with both ends failing their build on drift |
| Seed data | applied to the real migrated schema by 14 tests, including every door and every placed item |
| Release payload | all three components unpacked and checked before the executables are compiled |
| CMS dependency audit | 0 advisories (Laravel 12.69.3) |
| Launcher build | Linux and Windows, `go vet` clean, payload extracts on both |
| Observability | 27 Prometheus alerts, 15 Grafana dashboards |
| Placeholder scan | 0 findings |

Both dimensions that previously fell short now meet the bar: the wired
conformance suite exists and passes in full, and the CMS presents as a hotel
front end with a landing page, profiles, forums, a team page and statistics.

### What the earlier rows did not catch

Every row above was green while four things were true that no test looked at.
They are recorded here because the pattern matters more than the individual
bugs: a dimension passes on the thing it measures, and a feature nobody can
reach measures as complete.

| What was wrong | Why nothing noticed |
|----------------|---------------------|
| The client and the server spoke different protocols — around 300 server packets had no client name, around 50 client names had no handler | Each side's tests exercised its own table. Nothing compared the two. |
| A fresh hotel came up with no room shapes, no furniture and an empty catalogue | The install step called a Laravel seeder that does not exist, and the installer had no tests. |
| The website had no `public/index.php`, so every page would 404 behind a web server | The test suite boots the application directly rather than through a front controller. |
| A ban applied on the website was invisible to the hotel, which checks a different table | Both halves' tests passed against their own table. |
| The hotel and the roleplay city were one world wearing two names: `world_id` was on the rooms, the categories and the catalogue pages from the first migration and every service ignored it, so each world listed the other's rooms and sold the other's furniture | Nothing asked what a listing was supposed to contain, only that it returned rows. |

Each now has a test that fails the build rather than emptying the hotel.

Added since the original scope, from comparing against what running hotels and
downloadable emulators actually offer:

| Area | What it is |
|------|------------|
| Local edition | The whole hotel on one computer with `habnutctl dev up`, seeded and ready to sign into — see [LOCAL.md](LOCAL.md) |
| One account | Sign in once on the website and the hotel knows who walked in; the handover ticket is the hotel's own |
| Imager | Avatars and group badges rendered from the hotel's own asset pack, inside `habnutctl` — see [IMAGER.md](architecture/IMAGER.md) |
| Forums | Public boards and group boards in one place, with forum roles separate from hotel rank |
| Automated moderation | A content policy that permits adult conversation and stops what is harmful, with every automatic mute reviewed by a person — see [MODERATION.md](architecture/MODERATION.md) |
| Classic games | SnowStorm, Battle Ball, Wobble Squabble and Lido Diving on the modern engine |
| Visual eras | Classic and modern artwork, switchable without leaving the room or changing world |
| Faction treasuries | Territory income and heist takings, with a ledger |
| Heists | A crew, a target, an alarm and whichever side gets there first |
| Accessibility | Touch, keyboard walking, screen-reader room descriptions, reduced motion |

---

## Infrastructure Dimensions

| Component | Status |
|-----------|--------|
| Docker Compose (12 services) | ✅ complete |
| Nginx TLS + WS proxy | ✅ complete |
| Prometheus (27 alert rules) | ✅ complete |
| Grafana (15 dashboards) | ✅ complete |
| Loki + Promtail | ✅ complete |
| AlertManager (routing) | ✅ complete |
| MariaDB tuning | ✅ complete |
| habnutctl (29-step installer, 13-step updater) | ✅ complete |
| Avatar and badge imager | ✅ complete |
| Asset Validator | ✅ complete |
| Placeholder Scanner | ✅ complete |
| CI Pipeline (14 jobs) | ✅ complete |
| Flyway Migrations (V1–V15) | ✅ complete |
| Test Fixtures | ✅ complete |
