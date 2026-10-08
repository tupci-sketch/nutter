#!/bin/sh
# Habnut website container: waits for the database, applies the website's
# migrations, caches, serves. Artisan runs as www-data, the user php-fpm
# serves as, so nothing it writes is unreadable to the site.
set -e
cd /var/www/atom
artisan() { su -s /bin/sh -c "php artisan $*" www-data; }

mkdir -p storage/app/public storage/framework/cache/data storage/framework/sessions storage/framework/views storage/logs bootstrap/cache
chown -R www-data:www-data storage bootstrap/cache

echo "[cms] waiting for the database..."
until mariadb-admin ping -h"$DB_HOST" -u"$DB_USERNAME" -p"$DB_PASSWORD" --silent 2>/dev/null; do sleep 2; done

[ -L public/storage ] || artisan storage:link >/dev/null
# nginx serves the static files from a volume shared with this container;
# refreshed on every start so it always matches the code running here.
cp -a public/. /srv/public/
artisan migrate --force --no-interaction
artisan config:cache >/dev/null
artisan route:cache >/dev/null
artisan view:cache >/dev/null
echo "[cms] ready"
exec php-fpm
