<?php

namespace Tests\Feature;

use Tests\TestCase;

/**
 * Password resets stay off until outgoing mail is set up: the pages do not
 * exist and the sign-in page does not offer a link to them.
 */
class PasswordResetSwitchTest extends TestCase
{
    /** @test */
    public function resets_are_off_unless_switched_on(): void
    {
        $this->assertFalse(config('habnut.password_resets'));

        $this->get('/password/reset')->assertNotFound();
        $this->post('/password/email', ['email' => 'someone@example.com'])->assertNotFound();
        $this->get(route('login'))->assertOk()->assertDontSee('Forgot password?');
    }
}
