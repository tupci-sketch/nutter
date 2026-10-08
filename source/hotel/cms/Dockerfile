# Habnut website: Atom CMS (DennisObject/atomcms), pinned, with Habnut's
# overlay copied on top. Built on the server; nothing here is downloaded at
# start-up.
ARG ATOM_REF=f77e2b6545fcd293f09e819aaa6e0281f555ada4

FROM alpine/git AS source
ARG ATOM_REF
RUN git clone -q https://github.com/DennisObject/atomcms.git /src \
 && git -C /src checkout -q "$ATOM_REF" && rm -rf /src/.git
COPY overlay/ /src/

FROM composer:2 AS vendor
COPY --from=source /src /app
WORKDIR /app
RUN composer install --no-dev --no-interaction --no-progress --prefer-dist \
      --optimize-autoloader --no-scripts --ignore-platform-req=ext-*

FROM node:22 AS assets
# The theme imports Livewire's JavaScript from vendor/.
COPY --from=vendor /app /app
WORKDIR /app
RUN npm ci --no-audit --no-fund --loglevel=error && npm run build:atom

FROM php:8.5-fpm
ADD --chmod=0755 https://github.com/mlocati/docker-php-extension-installer/releases/latest/download/install-php-extensions /usr/local/bin/
RUN install-php-extensions gd intl pdo_mysql sockets bcmath zip opcache exif pcntl \
 && apt-get update -qq && apt-get install -y -qq --no-install-recommends mariadb-client \
 && rm -rf /var/lib/apt/lists/*
COPY php.ini /usr/local/etc/php/conf.d/zz-habnut.ini
COPY www.conf /usr/local/etc/php-fpm.d/zz-habnut.conf
WORKDIR /var/www/atom
COPY --from=source --chown=www-data:www-data /src /var/www/atom
COPY --from=vendor --chown=www-data:www-data /app/vendor /var/www/atom/vendor
COPY --from=assets --chown=www-data:www-data /app/public/build /var/www/atom/public/build
# Settings come from the container's environment; an empty .env it owns keeps
# Atom's tools from trying to create one in a directory it cannot write.
RUN touch /var/www/atom/.env && chown www-data:www-data /var/www/atom /var/www/atom/.env
# Package discovery and Filament's published assets, done once here rather than
# on every start. A throwaway key: nothing is encrypted at build time.
RUN cd /var/www/atom && APP_KEY=base64:$(head -c32 /dev/urandom | base64) APP_ENV=production DB_CONNECTION=sqlite \
      su -s /bin/sh -c "php artisan package:discover --ansi && php artisan filament:upgrade" www-data
COPY --chmod=0755 entrypoint.sh /usr/local/bin/habnut-cms
CMD ["habnut-cms"]
