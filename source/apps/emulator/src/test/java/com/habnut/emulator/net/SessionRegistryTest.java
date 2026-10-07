package com.habnut.emulator.net;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Session registry")
class SessionRegistryTest {

    @Test
    @DisplayName("an old session closing keeps the player's newer one, rank and all")
    void olderSessionClosingKeepsTheNewOne() {
        SessionRegistry sessions = new SessionRegistry();
        WebSocketSession first = sessions.register(new EmbeddedChannel());
        first.authenticate(7, "classic");
        sessions.onAuthenticated(first, 7, 7);

        WebSocketSession second = sessions.register(new EmbeddedChannel());
        second.authenticate(7, "classic");
        sessions.onAuthenticated(second, 7, 7);

        sessions.remove(first);

        assertSame(second, sessions.byUserId(7).orElseThrow());
        assertEquals(1, sessions.allStaff(4).size(), "the staff member fell off the staff list");

        sessions.remove(second);
        assertTrue(sessions.byUserId(7).isEmpty());
        assertTrue(sessions.allStaff(4).isEmpty());
    }
}
