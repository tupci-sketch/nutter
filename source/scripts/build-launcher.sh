#!/usr/bin/env bash
#
# Builds release habnutctl binaries for Linux and Windows with the emulator,
# client and CMS embedded, so a single executable can install a complete hotel
# on a fresh server.
#
# Usage:
#   scripts/build-launcher.sh              # build both platforms
#   scripts/build-launcher.sh linux        # build one platform
#
# Output: bin/habnutctl-linux-amd64, bin/habnutctl-windows-amd64.exe
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATA="$ROOT/apps/launcher/internal/payload/data"
# The released executables live beside the source tree rather than inside it:
# they are what somebody downloads, not something the build produces on the way
# to something else.
DIST="$ROOT/../bin"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

VERSION="$(cat "$ROOT/VERSION" 2>/dev/null || echo "1.0.0")"
TARGETS=("${@:-linux windows}")
read -ra TARGETS <<< "${TARGETS[*]}"

say() { printf '\n\033[1;33m==> %s\033[0m\n' "$*"; }

# ── 1. emulator ─────────────────────────────────────────────────────────────
say "Building emulator JAR"
# Uses the wrapper so the build does not depend on a Gradle installed on the
# host, which is what CI and a fresh clone both need.
( cd "$ROOT" && ./gradlew :emulator:jar -q --console=plain )
JAR="$ROOT/apps/emulator/build/libs/habnut-emulator.jar"
[ -f "$JAR" ] || { echo "emulator JAR not produced at $JAR" >&2; exit 1; }

# ── 2. client ───────────────────────────────────────────────────────────────
say "Building client bundle"
( cd "$ROOT/apps/client" && npx vite build )
[ -d "$ROOT/apps/client/dist" ] || { echo "client dist not produced" >&2; exit 1; }

# ── 3. CMS with production dependencies ─────────────────────────────────────
say "Building CMS production tree"
CMS="$STAGE/cms"
mkdir -p "$CMS"
for item in app bootstrap config database public resources routes artisan composer.json composer.lock; do
    cp -r "$ROOT/apps/cms/$item" "$CMS/" 2>/dev/null || true
done

# Laravel writes to storage/ and bootstrap/cache at runtime; ship the directory
# skeleton rather than any local contents.
mkdir -p "$CMS/storage/framework/"{cache,sessions,views} \
         "$CMS/storage/logs" \
         "$CMS/storage/app/public" \
         "$CMS/bootstrap/cache"
rm -rf "$CMS/bootstrap/cache/"*

( cd "$CMS" && composer install --no-dev --prefer-dist --no-scripts \
    --no-interaction --optimize-autoloader --quiet )

# Composer leaves full git history behind when a package resolves from source;
# it is never needed at runtime and dominates the archive size.
find "$CMS/vendor" -type d -name .git -prune -exec rm -rf {} + 2>/dev/null || true
find "$CMS/vendor" -type d \( -name tests -o -name Tests -o -name docs \) \
    -prune -exec rm -rf {} + 2>/dev/null || true

# ── 4. pack payload ─────────────────────────────────────────────────────────
say "Packing payload"
mkdir -p "$DATA"
cp "$JAR" "$DATA/habnut-emulator.jar"
tar czf "$DATA/client.tar.gz" -C "$ROOT/apps/client/dist" .
tar czf "$DATA/cms.tar.gz"    -C "$CMS" .
printf '%s' "$VERSION" > "$DATA/VERSION"

printf '  emulator : %s\n' "$(du -h "$DATA/habnut-emulator.jar" | cut -f1)"
printf '  client   : %s\n' "$(du -h "$DATA/client.tar.gz"       | cut -f1)"
printf '  cms      : %s\n' "$(du -h "$DATA/cms.tar.gz"          | cut -f1)"

# ── 5. cross-compile ────────────────────────────────────────────────────────
mkdir -p "$DIST"
LDFLAGS="-s -w -X main.version=$VERSION"

for target in "${TARGETS[@]}"; do
    case "$target" in
        linux)
            say "Building habnutctl for Linux (amd64)"
            ( cd "$ROOT/apps/launcher" && \
              CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
              go build -tags bundled -trimpath -ldflags "$LDFLAGS" \
                  -o "$DIST/habnutctl-linux-amd64" . )
            ;;
        windows)
            say "Building habnutctl for Windows (amd64)"
            ( cd "$ROOT/apps/launcher" && \
              CGO_ENABLED=0 GOOS=windows GOARCH=amd64 \
              go build -tags bundled -trimpath -ldflags "$LDFLAGS" \
                  -o "$DIST/habnutctl-windows-amd64.exe" . )
            ;;
        *)
            echo "unknown target: $target (expected 'linux' or 'windows')" >&2
            exit 1
            ;;
    esac
done

say "Done"
ls -lh "$DIST"
