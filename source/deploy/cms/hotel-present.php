<?php

// Exits 0 once the hotel's own schema is in the database, 1 until then.
// Used by the container's start-up to wait for the hotel before migrating.

chdir('/var/www/cms');
require 'vendor/autoload.php';
$app = require 'bootstrap/app.php';
$app->make(Illuminate\Contracts\Console\Kernel::class)->bootstrap();

try {
    exit(App\Support\HotelSchema::present() ? 0 : 1);
} catch (Throwable $e) {
    fwrite(STDERR, '[cms] not yet: '.$e->getMessage().PHP_EOL);
    exit(1);
}
