<?php

namespace Tests\Unit;

use Tests\TestCase;

/**
 * The website has to be servable, not just runnable.
 *
 * The test suite boots the application directly, so it passed perfectly well
 * with no `public/index.php` at all — meaning every page of the site 404'd
 * behind a web server, and nothing in the suite noticed. These check the parts
 * a web server needs rather than the parts a test runner does.
 */
class ApplicationShapeTest extends TestCase
{
    private function path(string $relative): string
    {
        return base_path($relative);
    }

    /** @test */
    public function the_website_has_a_front_controller_for_a_web_server_to_point_at(): void
    {
        $index = $this->path('public/index.php');

        $this->assertFileExists($index,
            'Without public/index.php a web server has nothing to serve: every page is a 404.');

        $body = file_get_contents($index);
        $this->assertStringContainsString('bootstrap/app.php', $body,
            'The front controller does not boot the application.');
        $this->assertStringContainsString('handleRequest', $body,
            'The front controller never hands the request to the application.');
    }

    /** @test */
    public function the_front_controller_is_the_only_php_in_the_public_directory(): void
    {
        $stray = [];
        foreach (glob($this->path('public/*.php')) as $file) {
            if (basename($file) !== 'index.php') {
                $stray[] = basename($file);
            }
        }

        $this->assertSame([], $stray,
            'Anything else in public/ is reachable from the internet without passing '
            .'through the application: '.implode(', ', $stray));
    }

    /** @test */
    public function the_staff_pages_are_kept_out_of_search_results(): void
    {
        $robots = $this->path('public/robots.txt');
        $this->assertFileExists($robots);

        $body = file_get_contents($robots);
        foreach (['/dcc', '/api'] as $path) {
            $this->assertStringContainsString("Disallow: {$path}", $body,
                "{$path} should not be indexed.");
        }
    }

    /** @test */
    public function the_application_can_be_booted_from_its_own_bootstrap_file(): void
    {
        $this->assertFileExists($this->path('bootstrap/app.php'));
        $this->assertFileExists($this->path('artisan'),
            'Without artisan, migrations cannot be run during an install.');
    }

    /** @test */
    public function every_directory_the_website_writes_to_exists(): void
    {
        foreach ([
            'storage/app',
            'storage/framework/cache',
            'storage/framework/sessions',
            'storage/framework/views',
            'storage/logs',
            'bootstrap/cache',
        ] as $dir) {
            $this->assertDirectoryExists($this->path($dir),
                "The website writes to {$dir}; a missing one fails the first request "
                .'with an error that says nothing about directories.');
        }
    }
}
