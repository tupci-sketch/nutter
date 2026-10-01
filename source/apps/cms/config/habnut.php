<?php

return [
    'emulator_host' => env('EMULATOR_HOST', '127.0.0.1'),
    'emulator_port' => env('EMULATOR_PORT', 8080),
    'grafana_url' => env('GRAFANA_URL', 'http://localhost:3000'),
    // The hotel reads this key itself, so it is its key, not Laravel's. See
    // App\Services\SessionTicketService.
    'redis_ticket_prefix' => 'habnut:ticket:',
    'ticket_ttl_minutes' => 5,

    // Where the game client is served. Same host as the site by default, which
    // keeps the session, the imager and the websocket all same-origin.
    'client_url' => env('CLIENT_URL', '/client/'),
    'brand_name' => 'Habnut',
    'brand_acorn' => '🌰',
    // The hotel renders its own avatars and badges, served alongside the site
    // so a picture never depends on another host being up.
    'imager_url' => env('IMAGER_URL', '/imager'),
    'hotel_name' => 'Hotel Habnut',
    'nutropolis_name' => 'Nutropolis',
];
