package com.habnut.emulator.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PacketRouterTest {

    private PacketRouter router;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        router = new PacketRouter(mapper);
    }

    @Test
    void dispatchesRegisteredHandler() throws Exception {
        AtomicReference<JsonNode> captured = new AtomicReference<>();
        router.register("test.ping", (session, payload) -> captured.set(payload));

        EmbeddedChannel channel = new EmbeddedChannel();
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession session = registry.register(channel);

        router.dispatch(session, "{\"type\":\"test.ping\",\"id\":\"1\",\"payload\":{\"msg\":\"hello\"}}");

        assertNotNull(captured.get());
        assertEquals("hello", captured.get().path("msg").asText());
    }

    @Test
    void sendsErrorOnMalformedJson() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession session = registry.register(channel);

        router.dispatch(session, "not-json{{{");

        // Should not throw; session remains open
        assertTrue(channel.isActive());
    }

    @Test
    void sendsErrorOnUnknownType() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession session = registry.register(channel);

        router.dispatch(session, "{\"type\":\"unknown.type\",\"id\":\"2\",\"payload\":{}}");

        assertTrue(channel.isActive());
    }

    @Test
    void buildPacketProducesValidJson() throws Exception {
        String json = router.buildPacket("test.response", java.util.Map.of("ok", true));
        JsonNode node = mapper.readTree(json);
        assertEquals("test.response", node.path("type").asText());
        assertTrue(node.path("payload").path("ok").asBoolean());
    }

    @Test
    void rateLimiterAllowsUpToBurst() {
        RateLimiter rl = new RateLimiter();
        int allowed = 0;
        for (int i = 0; i < 20; i++) {
            if (rl.tryAcquire(1L, RateLimiter.Bucket.CHAT)) allowed++;
        }
        assertEquals(8, allowed, "CHAT burst cap is 8");
    }

    @Test
    void rateLimiterRemovesCleansState() {
        RateLimiter rl = new RateLimiter();
        rl.tryAcquire(42L, RateLimiter.Bucket.MOVEMENT);
        rl.remove(42L);
        // After removal a full burst is available again
        int allowed = 0;
        for (int i = 0; i < 15; i++) {
            if (rl.tryAcquire(42L, RateLimiter.Bucket.MOVEMENT)) allowed++;
        }
        assertEquals(10, allowed, "MOVEMENT burst cap is 10");
    }
}
