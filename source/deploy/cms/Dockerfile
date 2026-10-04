# Habnut — the website, for production.
#
# The same extensions as the local edition's image, which is the one shown to
# work. Built on the server from the release; the context is the release's top
# directory, and Dockerfile.dockerignore keeps everything but the website and
# this directory out of it.

FROM php:8.3-fpm-alpine

# Compiled once and cached: this layer only changes when the extensions do.
RUN set -eux; \
    apk add --no-cache --virtual .build-deps $PHPIZE_DEPS; \
    docker-php-ext-install -j"$(nproc)" pdo_mysql bcmath pcntl opcache; \
    apk del .build-deps

COPY deploy/cms/php.ini /usr/local/etc/php/conf.d/zz-habnut.ini
COPY deploy/cms/www.conf /usr/local/etc/php-fpm.d/zz-habnut.conf
COPY deploy/cms/entrypoint.sh /usr/local/bin/habnut-cms-entrypoint
COPY deploy/cms/hotel-present.php /usr/local/bin/habnut-hotel-present.php

# The code, owned by the user php-fpm serves as so Laravel can write its
# caches. storage/ and .env are mounted over the top from the host.
COPY --chown=www-data:www-data cms/ /var/www/cms/

WORKDIR /var/www/cms
CMD ["sh", "/usr/local/bin/habnut-cms-entrypoint"]
