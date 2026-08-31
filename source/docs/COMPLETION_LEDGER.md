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
| Emulator unit tests | 187 passing |
| Wired 2.0 conformance | 254 passing, the Chapter 59.1 gate met |
| Wired 2.0 registry | 21 triggers · 56 actions · 37 conditions · 22 selectors |
| Client tests | 79 passing; typecheck and lint clean |
| CMS tests | 106 passing |
| Launcher tests | 46 imager, plus extractor and payload suites |
| Coverage floors | enforced per module, build fails below them |
| Database migrations | V1–V15, applied in order against H2 by the schema guard |
| Schema guard | every column the services select **and insert into** checked against the migrations |
| CMS dependency audit | 0 advisories (Laravel 12.66) |
| Launcher build | Linux and Windows, `go vet` clean, payload extracts on both |
| Observability | 27 Prometheus alerts, 15 Grafana dashboards |
| Placeholder scan | 0 findings |

Both dimensions that previously fell short now meet the bar: the wired
conformance suite exists and passes in full, and the CMS presents as a hotel
front end with a landing page, profiles, forums, a team page and statistics.

Added since the original scope, from comparing against what running hotels and
downloadable emulators actually offer:

| Area | What it is |
|------|------------|
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
