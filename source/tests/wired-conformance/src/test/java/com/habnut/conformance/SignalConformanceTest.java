package com.habnut.conformance;

import com.habnut.emulator.wired.WiredSignalBus;
import com.habnut.emulator.wired.WiredValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The signal bus: room-local delivery, global broadcast, and the rate limit
 * that keeps a global signal from being used to flood every room on the hotel.
 */
@DisplayName("Wired signals")
class SignalConformanceTest {

    private WiredSignalBus bus;

    @BeforeEach
    void setUp() {
        bus = new WiredSignalBus();
    }

    private record Received(long roomId, String channel, String payload) {}

    @Test
    @DisplayName("a room signal reaches only that room")
    void roomSignalIsLocal() {
        List<Received> roomA = new CopyOnWriteArrayList<>();
        List<Received> roomB = new CopyOnWriteArrayList<>();

        bus.subscribe(100L, (r, c, p) -> roomA.add(new Received(r, c, p.asText())));
        bus.subscribe(200L, (r, c, p) -> roomB.add(new Received(r, c, p.asText())));

        bus.emit(100L, "round_start", WiredValue.ofText("go"));

        assertEquals(1, roomA.size(), "the emitting room should receive its own signal");
        assertEquals("round_start", roomA.get(0).channel());
        assertEquals("go", roomA.get(0).payload());
        assertTrue(roomB.isEmpty(), "another room must not receive a room-local signal");
    }

    @Test
    @DisplayName("every listener in a room receives the signal")
    void allListenersInARoomReceive() {
        AtomicInteger count = new AtomicInteger();
        bus.subscribe(100L, (r, c, p) -> count.incrementAndGet());
        bus.subscribe(100L, (r, c, p) -> count.incrementAndGet());

        bus.emit(100L, "ping", WiredValue.EMPTY);

        assertEquals(2, count.get());
    }

    @Test
    @DisplayName("emitting into a room with no listeners is harmless")
    void emitToEmptyRoom() {
        assertDoesNotThrow(() -> bus.emit(999L, "nobody_home", WiredValue.EMPTY));
    }

    @Test
    @DisplayName("a throwing listener does not stop the others")
    void throwingListenerIsContained() {
        AtomicInteger delivered = new AtomicInteger();
        bus.subscribe(100L, (r, c, p) -> { throw new IllegalStateException("listener failed"); });
        bus.subscribe(100L, (r, c, p) -> delivered.incrementAndGet());

        assertDoesNotThrow(() -> bus.emit(100L, "ping", WiredValue.EMPTY));
        assertEquals(1, delivered.get(), "the healthy listener must still receive the signal");
    }

    @Test
    @DisplayName("unsubscribing a room stops delivery to it")
    void unsubscribe() {
        AtomicInteger count = new AtomicInteger();
        bus.subscribe(100L, (r, c, p) -> count.incrementAndGet());

        bus.unsubscribeAll(100L);
        bus.emit(100L, "ping", WiredValue.EMPTY);

        assertEquals(0, count.get());
    }

    @Test
    @DisplayName("a global signal reaches every room")
    void globalSignalCrossesRooms() {
        AtomicInteger roomA = new AtomicInteger();
        AtomicInteger roomB = new AtomicInteger();
        bus.subscribe(100L, (r, c, p) -> roomA.incrementAndGet());
        bus.subscribe(200L, (r, c, p) -> roomB.incrementAndGet());

        assertTrue(bus.emitGlobal("hotel_event", WiredValue.ofText("started")));

        assertEquals(1, roomA.get());
        assertEquals(1, roomB.get());
    }

    @Test
    @DisplayName("a global signal is delivered with the broadcast room marker")
    void globalSignalCarriesBroadcastMarker() {
        List<Received> received = new CopyOnWriteArrayList<>();
        bus.subscribe(100L, (r, c, p) -> received.add(new Received(r, c, p.asText())));

        bus.emitGlobal("hotel_event", WiredValue.ofText("x"));

        assertEquals(-1L, received.get(0).roomId(),
            "listeners distinguish a broadcast from a room-local signal by this marker");
    }

    @Test
    @DisplayName("global signals are rate limited per channel")
    void globalRateLimit() {
        bus.subscribe(100L, (r, c, p) -> { });

        int accepted = 0;
        for (int i = 0; i < 20; i++) {
            if (bus.emitGlobal("spam", WiredValue.EMPTY)) accepted++;
        }

        assertEquals(10, accepted,
            "the limiter should admit exactly the documented 10 per window");
    }

    @Test
    @DisplayName("the rate limit is tracked separately for each channel")
    void rateLimitIsPerChannel() {
        bus.subscribe(100L, (r, c, p) -> { });

        for (int i = 0; i < 15; i++) bus.emitGlobal("channel_a", WiredValue.EMPTY);

        assertTrue(bus.emitGlobal("channel_b", WiredValue.EMPTY),
            "flooding one channel must not silence another");
    }

    @Test
    @DisplayName("the rate limit window reopens over time")
    void rateLimitWindowReopens() throws InterruptedException {
        bus.subscribe(100L, (r, c, p) -> { });

        for (int i = 0; i < 15; i++) bus.emitGlobal("burst", WiredValue.EMPTY);
        assertFalse(bus.emitGlobal("burst", WiredValue.EMPTY), "window is exhausted");

        Thread.sleep(1_100);

        assertTrue(bus.emitGlobal("burst", WiredValue.EMPTY),
            "a new window should admit signals again");
    }
}
