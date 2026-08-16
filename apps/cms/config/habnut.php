<?php

return [
    'emulator_host'  => env('EMULATOR_HOST', '127.0.0.1'),
    'emulator_port'  => env('EMULATOR_PORT', 8080),
    'grafana_url'    => env('GRAFANA_URL', 'http://localhost:3000'),
    'redis_ticket_prefix' => 'ticket:',
    'ticket_ttl_minutes'  => 5,
    'brand_name'     => 'Habnut',
    'brand_acorn'    => '🌰',
    'imager_url'     => env('IMAGER_URL'),
    'hotel_name'     => 'Hotel Habnut',
    'nutropolis_name'=> 'Nutropolis',
];
