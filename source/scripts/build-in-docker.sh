#!/usr/bin/env bash
#
# Builds the release habnutctl binaries from source with nothing installed but
# Docker: each part is built in the official image for its toolchain, the same
# versions CI uses. The steps are build-launcher.sh's; only where they run
# differs. Run it on the server or any Linux machine with Docker.
#
#   scripts/build-in-docker.sh            # writes bin/habnutctl-{linux-amd64,windows-amd64.exe}
#
# Downloads (Gradle, npm, Composer, Go modules) are cached in ~/.cache/habnut-build.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST="$ROOT/../bin"
DATA="$ROOT/apps/launcher/internal/payload/data"
CACHE="${HABNUT_BUILD_CACHE:-$HOME/.cache/habnut-build}"
VERSION="$(tr -d '[:space:]' < "$ROOT/VERSION")"
DOCKER=(docker)
docker info >/dev/null 2>&1 || DOCKER=(sudo docker)

mkdir -p "$CACHE"/{gradle,pnpm,composer,go}
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

say() { printf '\n\033[1;33m==> %s\033[0m\n' "$*"; }

# in_image <image> <dir under source> [docker options...] -- <command...>
# As the calling user, so nothing in the tree ends up owned by root.
in_image() {
  local image=$1 dir=$2 opts=()
  shift 2
  while [ $# -gt 0 ] && [ "$1" != "--" ]; do opts+=("$1"); shift; done
  shift
  "${DOCKER[@]}" run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp \
    -v "$CACHE":/cache -v "$ROOT":/src -v "$STAGE":/stage -w "/src/$dir" \
    "${opts[@]}" "$image" "$@"
}

say "Emulator (Gradle, JDK 21)"
in_image eclipse-temurin:21-jdk . -e GRADLE_USER_HOME=/cache/gradle -- \
  sh -c './gradlew :emulator:jar --no-daemon -q --console=plain'
JAR="$ROOT/apps/emulator/build/libs/habnut-emulator.jar"
[ -f "$JAR" ] || { echo "emulator JAR not produced" >&2; exit 1; }

say "Client (Node 22, pnpm)"
in_image node:22 . -e npm_config_store_dir=/cache/pnpm -e COREPACK_HOME=/cache/pnpm/corepack -- \
  sh -c 'corepack pnpm install --frozen-lockfile --silent && cd apps/client && npx vite build --logLevel warn'
[ -f "$ROOT/apps/client/dist/index.html" ] || { echo "client not produced" >&2; exit 1; }

say "Website with production dependencies (Composer)"
CMS="$STAGE/cms"
mkdir -p "$CMS"
for item in app bootstrap config database public resources routes artisan composer.json composer.lock; do
  cp -r "$ROOT/apps/cms/$item" "$CMS/"
done
mkdir -p "$CMS/storage/framework/"{cache,sessions,views} "$CMS/storage/logs" \
         "$CMS/storage/app/public" "$CMS/bootstrap/cache"
rm -rf "$CMS/bootstrap/cache/"*
in_image composer:2 . -e COMPOSER_HOME=/cache/composer -w /stage/cms -- \
  composer install --no-dev --prefer-dist --no-scripts --no-interaction \
    --optimize-autoloader --quiet --ignore-platform-req=ext-*
find "$CMS/vendor" -type d -name .git -prune -exec rm -rf {} + 2>/dev/null || true
find "$CMS/vendor" -type d \( -name tests -o -name Tests -o -name docs -o -name test_files \) \
  -prune -exec rm -rf {} + 2>/dev/null || true

say "Packing the payload"
mkdir -p "$DATA"
cp "$JAR" "$DATA/habnut-emulator.jar"
tar czf "$DATA/client.tar.gz" -C "$ROOT/apps/client/dist" .
tar czf "$DATA/cms.tar.gz"    -C "$CMS" .
printf '%s' "$VERSION" > "$DATA/VERSION"

say "Launcher (Go 1.24): checking the payload, then building"
mkdir -p "$DIST"
in_image golang:1.24 apps/launcher -e GOPATH=/cache/go -e GOCACHE=/cache/go/build -e CGO_ENABLED=0 -- sh -c "
  go test -tags bundled ./internal/payload/ &&
  LDFLAGS='-s -w -X main.version=$VERSION' &&
  GOOS=linux   GOARCH=amd64 go build -tags bundled -trimpath -ldflags \"\$LDFLAGS\" -o /stage/habnutctl-linux-amd64 . &&
  GOOS=windows GOARCH=amd64 go build -tags bundled -trimpath -ldflags \"\$LDFLAGS\" -o /stage/habnutctl-windows-amd64.exe ."
cp "$STAGE/habnutctl-linux-amd64" "$STAGE/habnutctl-windows-amd64.exe" "$DIST/"

say "Done"
ls -lh "$DIST"
