#!/bin/sh
# Builds Habnut Emulator: Polaris (duckietm/Polaris-Emulator) at a pinned
# commit, branded by brand.py, plus Habnut's own plugins from plugins/.
#
#     sudo sh build.sh /srv/habnut/hotel/emulator
#
# Writes habnut-emulator.jar and plugins/*.jar into the given folder; the
# running emulator picks them up on its next restart.
set -eu
POLARIS_REF=0101ce955069af1bcf1dd4299b03250679d9b1e1
OUT=${1:?usage: build.sh <emulator-dir>}
HERE=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
M2=/var/cache/habnut-build/m2
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$M2"

git clone -q https://github.com/duckietm/Polaris-Emulator.git "$WORK/polaris"
git -C "$WORK/polaris" checkout -q "$POLARIS_REF"
python3 -I "$HERE/brand.py" "$WORK/polaris"

docker run --rm -v "$M2":/root/.m2 -v "$WORK/polaris/Emulator":/src -w /src \
    maven:3-eclipse-temurin-25 mvn -q -B -DskipTests -Dspotless.check.skip=true -Dspotless.apply.skip=true install
JAR=$(ls "$WORK"/polaris/Emulator/target/*-jar-with-dependencies.jar 2>/dev/null || ls "$WORK"/polaris/Emulator/target/*.jar | grep -v original | head -1)

for plugin in "$HERE"/plugins/*/; do
    [ -f "$plugin/pom.xml" ] || continue
    cp -a "$plugin" "$WORK/plugin"
    docker run --rm -v "$M2":/root/.m2 -v "$WORK/plugin":/src -w /src \
        maven:3-eclipse-temurin-25 mvn -q -B -DskipTests package
    mkdir -p "$OUT/plugins"
    cp "$WORK"/plugin/target/*.jar "$OUT/plugins/"
    rm -rf "$WORK/plugin"
done

cp "$JAR" "$OUT/habnut-emulator.jar"
echo "built $OUT/habnut-emulator.jar from Polaris $POLARIS_REF"
