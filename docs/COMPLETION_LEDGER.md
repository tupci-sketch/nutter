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
| Emulator compiles (Java 21) | 123 source files, 17,160 LOC |
| Emulator unit tests | 19 passing |
| Emulator persistence | 287 prepared statements across 47 classes |
| Packet routing | 303 packet types, all handlers registered at boot |
| Wired 2.0 registry | 21 triggers · 56 actions · 36 conditions · 22 selectors |
| Database migrations | V1–V9, 118 tables |
| Client typecheck and build | passing, 534 modules |
| CMS tests | 36 passing |
| CMS dependency audit | 0 advisories (Laravel 12.66) |
| Launcher build | Linux and Windows, `go vet` clean |
| Launcher payload | extracts and runs on both targets |
| Observability | 27 Prometheus alerts, 15 Grafana dashboards |
| Placeholder scan of application source | 0 findings |

Not yet meeting the claimed bar:

| Dimension | Actual state | Consequence |
|-----------|--------------|-------------|
| **TEST** | 57 automated tests total (19 emulator, 36 CMS, 2 client). `tests/wired-conformance/` contains no cases. | The TEST column is not green for most rows. Chapter 59.1 gates game work on a 100%-passing wired conformance suite; that gate is unmet because the suite does not exist. |
| **UI** (CMS only) | 68 Blade views totalling 1,712 lines, averaging 25 lines each. Routes, controllers and permissions are complete; the templates are minimal. | The CMS functions but does not yet present like an established hotel front-end. |

Wired condition count is 36 against a specified 37; the missing condition is
tracked as the one registry gap.

| Code | Feature Area | DB | SVC | PKT | UI | DCC | PERM | TEST | METRIC | ASSET | DOC | Status |
|------|-------------|----|-----|-----|----|-----|------|------|--------|-------|-----|--------|
| AUT | Authentication | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| ROM | Rooms | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| NAV | Navigator | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| FUR | Furniture | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| CAT | Catalogue | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| INV | Inventory | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| TRD | Trading | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| MKT | Marketplace | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| FRI | Friends | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| MSG | Messaging | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| GRP | Groups | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| BDG | Badges | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| ACH | Achievements | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| QST | Quests | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| PRF | Profile | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| EVT | Events | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| CMP | Competitions | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| TRN | Tournaments | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| SEA | Seasons | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| MOD | Moderation | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| STF | Staff Tools | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| ECO | Economy Core | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| PRO | Membership/Pro | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| GRD | Community Garden | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| RPC | RP Characters | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| RPJ | RP Jobs | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| RPF | RP Factions | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| RPP | RP Properties | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| RPV | RP Vehicles | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| RPB | RP Bank | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| RPG | RP Government | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| RPL | RP Legal/Courts | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| RPE | RP Medical | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| RPI | RP Dispatch | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| RPK | RP Crimes | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| RPA | RP Scenes/Crafting | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| CMS | CMS Public | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| DCC | DCC Admin | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| PET | Pets | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| BOT | Bots | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| CAM | Camera | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| SND | Sound | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| NOT | Notifications | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |
| FLG | Feature Flags | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **complete** |

**Total: 44 / 44 feature areas implemented across DB, SVC, PKT, DCC, PERM,
METRIC and ASSET. TEST is not green for most rows and CMS UI is minimal — see
[Verification status](#verification-status).**

---

## Infrastructure Dimensions

| Component | Status |
|-----------|--------|
| Docker Compose (11 services) | ✅ complete |
| Nginx TLS + WS proxy | ✅ complete |
| Prometheus (27 alert rules) | ✅ complete |
| Grafana (15 dashboards) | ✅ complete |
| Loki + Promtail | ✅ complete |
| AlertManager (routing) | ✅ complete |
| MariaDB tuning | ✅ complete |
| habnutctl (28-step installer, 13-step updater) | ✅ complete |
| Asset Validator | ✅ complete |
| Placeholder Scanner | ✅ complete |
| CI Pipeline (15 steps) | ✅ complete |
| Flyway Migrations (V1–V8) | ✅ complete |
| Test Fixtures | ✅ complete |
