#!/bin/sh
# Builds the Habnut client: Octane and Octane-Renderer (duckietm) at pinned
# commits, branded by brand.py.
#
#     sudo sh build.sh /srv/habnut/hotel/client
#
# The finished client is synced into the given folder; afterwards rerun
# write-client-config.py, which reads its configuration/ templates.
set -eu
OCTANE_REF=7c0340f42fae0052955e395190c1094ccfca1397
RENDERER_REF=5b09f08cd42d2d507bad2c1ec43087ce834828fc
OUT=${1:?usage: build.sh <client-dir>}
HERE=$(cd "$(dirname "$0")" && pwd)
CACHE=/var/cache/habnut-build
WORK=$CACHE/client-work
rm -rf "$WORK"; mkdir -p "$WORK" "$CACHE/yarn" "$CACHE/corepack"
trap 'rm -rf "$WORK"' EXIT

git clone -q https://github.com/duckietm/Octane.git "$WORK/octane"
git -C "$WORK/octane" checkout -q "$OCTANE_REF"
# The client builds against the renderer in the folder beside it.
git clone -q https://github.com/duckietm/Octane-Renderer.git "$WORK/octane-renderer"
git -C "$WORK/octane-renderer" checkout -q "$RENDERER_REF"
python3 -I "$HERE/brand.py" "$WORK/octane" "$WORK/octane-renderer" "$HERE/icon.png"
python3 -I "$HERE/fixes.py" "$WORK/octane"
cp "$HERE/logo-large.png" "$WORK/octane/public/habnut-logo.png"

docker run --rm -e COREPACK_ENABLE_DOWNLOAD_PROMPT=0 -e COREPACK_HOME=/cache/corepack \
    -e YARN_CACHE_FOLDER=/cache/yarn -v "$CACHE":/cache -v "$WORK":/w -w /w node:22 \
    sh -c 'cd octane-renderer && corepack yarn install && cd ../octane && corepack yarn install && corepack yarn build'

# Update the folder in place: the web server has it bind-mounted, and a
# folder swapped for a new one would leave it serving the deleted original.
mkdir -p "$OUT"
rsync -a --delete "$WORK/octane/dist/" "$OUT/"
echo "built the Habnut client into $OUT"
