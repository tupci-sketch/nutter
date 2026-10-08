#!/bin/sh
# Builds habnut-imager: duckietm/Polaris-imager at a pinned commit, patched
# for the current renderer, which reads its settings from window.OctaneConfig
# (the imager still sets the old window.NitroConfig).
#
#     sudo sh build.sh [tag]
set -eu
IMAGER_REF=8a3250e425371ee392ad8fd87312ca40486ba12f
TAG=${1:-dev}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
git clone -q https://github.com/duckietm/Polaris-imager.git "$WORK/imager"
git -C "$WORK/imager" checkout -q "$IMAGER_REF"
sed -i 's|^\(\s*\)globalThis.NitroConfig = config;|&\n\1globalThis.OctaneConfig = config;|' "$WORK/imager/src/renderer.mjs"
grep -q 'globalThis.OctaneConfig = config;' "$WORK/imager/src/renderer.mjs"
docker build -q -t "habnut-imager:$TAG" --build-arg RENDERER_REF=main "$WORK/imager"
