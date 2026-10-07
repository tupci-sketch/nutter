#!/usr/bin/env bash
#
# Builds the release a server deploys: habnut-<version>.zip, plus its .sha256.
#
#   scripts/package-release.sh [output-dir]
#
# The hotel, the website and the game come from inside bin/habnutctl-linux-amd64,
# the binary CI builds and the local edition runs, so the server gets exactly
# the components that were tested rather than a second build of them. Nothing
# is compiled here, which matters on a two-core server.
#
# The launcher only unpacks its payload as part of 'dev up', and checks Docker
# before it starts. A stand-in docker that passes that check and refuses
# everything else lets it unpack and then stop, without touching the real one.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO="$(cd "$ROOT/.." && pwd)"
OUT="${1:-$REPO/dist}"
BIN="$REPO/bin/habnutctl-linux-amd64"

[ -x "$BIN" ] || { echo "missing $BIN" >&2; exit 1; }

VERSION="$(tr -d '[:space:]' < "$ROOT/VERSION")-$(git -C "$REPO" rev-parse --short HEAD)"
if [ -n "$(git -C "$REPO" status --porcelain -- source/deploy bin)" ]; then
  VERSION="$VERSION-dirty"
fi
NAME="habnut-$VERSION"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "==> Unpacking the tested components from $(basename "$BIN")"
mkdir -p "$WORK/fakebin"
cat > "$WORK/fakebin/docker" <<'EOF'
#!/bin/sh
case "$1 $2" in "info"*|"compose version"*|"version"*) echo "stand-in"; exit 0 ;; esac
exit 1
EOF
chmod +x "$WORK/fakebin/docker"
PATH="$WORK/fakebin:$PATH" "$BIN" dev up --dir "$WORK/unpacked" >/dev/null 2>&1 || true
for part in emulator/habnut-emulator.jar client/index.html cms/artisan cms/vendor/autoload.php; do
  [ -e "$WORK/unpacked/$part" ] || { echo "the binary did not unpack $part" >&2; exit 1; }
done

echo "==> Assembling $NAME"
R="$WORK/$NAME"
mkdir -p "$R/bin" "$R/seed"
mv "$WORK/unpacked/emulator" "$WORK/unpacked/client" "$WORK/unpacked/cms" "$R/"
# The local edition's own settings and start-up script, never wanted here.
rm -f "$R/cms/.env" "$R/cms/dev-entrypoint.sh"
rm -rf "$R/cms/storage/logs/"* "$R/cms/storage/framework/sessions/"* "$R/cms/bootstrap/cache/"*.php
# Libraries' own test fixtures: never run on a server, and some are SSH keys.
find "$R/cms/vendor" -type d \( -name test_files -o -name tests -o -name Tests \) -prune -exec rm -rf {} +
cp "$BIN" "$R/bin/habnutctl"
cp -r "$ROOT/deploy" "$R/deploy"
# Only the hotel's starting content. demo.sql holds sign-in accounts with a
# known password and must never reach a server.
cp "$ROOT/apps/launcher/internal/seed/sql/base.sql" "$R/seed/base.sql"
echo "$VERSION" > "$R/VERSION"

echo "==> Checking nothing secret is inside"
if find "$R" \( -name '.env' -o -name '*.pem' -o -name '*.key' -o -name 'id_rsa*' -o -name 'id_ed25519*' -o -name 'id_ecdsa*' -o -name 'id_dsa*' -o -name 'demo.sql' \) | grep -q .; then
  find "$R" \( -name '.env' -o -name '*.pem' -o -name '*.key' -o -name 'id_rsa*' -o -name 'id_ed25519*' -o -name 'id_ecdsa*' -o -name 'id_dsa*' -o -name 'demo.sql' \) >&2
  echo "refusing to package: the files above must not be in a release" >&2
  exit 1
fi

(cd "$R" && find . -type f ! -name MANIFEST.sha256 -print0 | sort -z | xargs -0 sha256sum > MANIFEST.sha256)

mkdir -p "$OUT"
rm -f "$OUT/$NAME.zip"
(cd "$WORK" && zip -qr -y "$OUT/$NAME.zip" "$NAME")
(cd "$OUT" && sha256sum "$NAME.zip" > "$NAME.zip.sha256")
echo "==> $OUT/$NAME.zip ($(du -h "$OUT/$NAME.zip" | cut -f1))"
