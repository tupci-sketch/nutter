# Habnut

Habnut is a complete virtual hotel and roleplay platform built for modern players who know what a hotel should feel like. It ships the Classic hotel experience with full feature parity, a complete Wired 2.0 scripting engine that goes beyond any competitor, and Nutropolis — a persistent roleplay world with factions, jobs, property, government and law — all in one platform under one roof, operated by a single command.

## Worlds

**Classic** — The hotel. Rooms, furniture, games, trading, the catalogue, groups, the Community Garden, and everything a returning player expects to find on day one.

**Nutropolis** — The city. A persistent roleplay world with characters, factions, vehicles, properties, businesses, employment, banking, government, law enforcement, courts and a full civic system.

## What makes it different

- Wired 2.0 is implemented completely: every trigger, every action, every condition, every selector — with a live debugger, variable inspector, signal system and exportable stack configurations.
- No button does nothing. No wired box does nothing. No game cannot be finished. No item disappears when the server restarts.
- A single binary — `habnutctl` — installs, updates, backs up, restores and recovers the entire platform on any Ubuntu VPS.

## Quick start (operators)

```sh
curl -Lo habnutctl https://releases.habnut.example/habnutctl-linux-amd64
chmod +x habnutctl
sudo ./habnutctl install
```

Follow the prompts. The installer handles everything: dependencies, Docker, database, asset pack, TLS, monitoring. Under twenty minutes from bare VPS to live hotel.

## Repository layout

```
apps/
  emulator/     Game server — Java 21, Netty
  client/       Web client — TypeScript, React, PixiJS
  cms/          Public site and DCC admin — Laravel 11
  launcher/     habnutctl CLI — Go
packages/
  protocol/     Shared packet definitions
  shared-types/ Cross-stack type contracts
  telemetry/    Shared telemetry event shapes
  test-fixtures/ Deterministic test data
infra/
  docker/       Docker Compose configuration
  nginx/        Nginx configuration templates
  prometheus/   Metrics rules and alerts
  grafana/      Dashboard definitions
  loki/         Log aggregation configuration
  templates/    Environment file templates
tools/
  asset-validator/    Asset integrity validation
  placeholder-scan/   Prohibited pattern detection
tests/
  wired-conformance/  Wired 2.0 conformance suite
docs/           Architecture, protocols, runbooks, completion tracking
```

## Development

Requires: Java 21, Go 1.24+, Node.js 20+, pnpm 8+, Docker 24+.

```sh
pnpm install
pnpm build
pnpm test
```

See `docs/architecture/ARCHITECTURE_OVERVIEW.md` for the full system design.
