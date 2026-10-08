#!/bin/sh
# Backs up the hotel's database: a consistent dump, compressed, kept for
# two weeks. Run daily by habnut-hotel-backup.timer.
#
#     sudo sh backup.sh [/srv/habnut/hotel]
set -eu
ROOT=${1:-/srv/habnut/hotel}
KEEP_DAYS=${KEEP_DAYS:-14}
DIR="$ROOT/backups"
mkdir -p "$DIR"
chmod 700 "$DIR"
. "$ROOT/.env"
STAMP=$(date -u +%Y%m%d-%H%M%S)
OUT="$DIR/habnut-$STAMP.sql.gz"
docker exec -e MYSQL_PWD="$DB_ROOT_PASSWORD" hotel-db-1 \
    mariadb-dump -uroot --single-transaction --quick --routines --triggers --events habnut \
    | gzip -6 > "$OUT.part"
# A dump that ends without its completion line was cut short.
gzip -dc "$OUT.part" | tail -1 | grep -q "Dump completed" || { rm -f "$OUT.part"; echo "backup incomplete" >&2; exit 1; }
mv "$OUT.part" "$OUT"
chmod 600 "$OUT"
find "$DIR" -name 'habnut-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "backed up to $OUT ($(du -h "$OUT" | cut -f1))"
