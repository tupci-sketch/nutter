#!/bin/sh
# Habnut — the website's container, in production.
#
# Waits for the hotel's schema, brings the website's own tables up to date,
# caches what Laravel can cache, and serves. Every artisan command runs as
# www-data, the user php-fpm serves as: run as root, the first thing one logged
# left a log file php-fpm could not write, and every page became a 500.
set -e
cd /var/www/cms

artisan() { su -s /bin/sh -c "php artisan $*" www-data; }

echo "[cms] clearing cached settings..."
artisan config:clear >/dev/null

# The website's migrations describe the hotel's tables only for the website's
# own tests, and leave them alone once the hotel's schema is present. Run
# before the hotel had made its schema, they would create the hotel's tables in
# the website's shape instead, so this waits for the hotel.
echo "[cms] waiting for the hotel's schema..."
tries=0
until su -s /bin/sh -c "php /usr/local/bin/habnut-hotel-present.php" www-data; do
  tries=$((tries + 1))
  if [ "$tries" -gt 60 ]; then
    echo "[cms] the hotel's schema never appeared; is the hotel running?" >&2
    exit 1
  fi
  sleep 3
done

echo "[cms] applying website migrations..."
artisan migrate --force --no-interaction

echo "[cms] caching settings, routes, views and events..."
artisan config:cache
artisan route:cache
artisan view:cache
artisan event:cache

echo "[cms] ready"
exec php-fpm
