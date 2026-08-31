package com.habnut.emulator.auth;

import com.habnut.emulator.config.ServerConfig;
import com.habnut.emulator.redis.RedisManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionTicketServiceTest {

    @Mock RedisManager redis;

    private SessionTicketService service;

    @BeforeEach
    void setUp() {
        ServerConfig config = new ServerConfig.Builder()
            .dbUrl("jdbc:mariadb://localhost/test")
            .dbUsername("test")
            .dbPassword("test")
            .sessionTicketTtlSeconds(300)
            .build();
        service = new SessionTicketService(redis, config);
    }

    @Test
    void issueStoresTicketInRedis() {
        String ticket = service.issue(42L, "classic");
        assertNotNull(ticket);
        assertFalse(ticket.isBlank());
        verify(redis).setex(eq("habnut:ticket:" + ticket), eq(300L), eq("42:classic"));
    }

    @Test
    void consumeReturnsClaimAndDeletesKey() {
        String ticket = "abc123";
        when(redis.get("habnut:ticket:" + ticket)).thenReturn("99:nutropolis");

        SessionTicketService.TicketClaim claim = service.consume(ticket);

        assertNotNull(claim);
        assertEquals(99L, claim.userId());
        assertEquals("nutropolis", claim.worldId());
        verify(redis).del("habnut:ticket:" + ticket);
    }

    @Test
    void consumeReturnsNullForMissingTicket() {
        when(redis.get(anyString())).thenReturn(null);
        assertNull(service.consume("missing"));
    }

    @Test
    void issuedTicketIsUniqueEachTime() {
        String t1 = service.issue(1L, "classic");
        String t2 = service.issue(1L, "classic");
        assertNotEquals(t1, t2);
    }
}
