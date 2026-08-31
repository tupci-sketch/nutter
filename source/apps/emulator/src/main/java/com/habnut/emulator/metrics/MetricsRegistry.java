package com.habnut.emulator.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.jvm.*;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public final class MetricsRegistry {

    private static final Logger log = LoggerFactory.getLogger(MetricsRegistry.class);

    private final PrometheusMeterRegistry registry;

    private final AtomicInteger connectedPlayers = new AtomicInteger(0);
    private final AtomicInteger activeRooms = new AtomicInteger(0);
    private final AtomicInteger dbPoolActive = new AtomicInteger(0);
    private final AtomicInteger dbPoolIdle = new AtomicInteger(0);

    private final Counter packetsReceived;
    private final Counter packetsSent;
    private final Counter packetsDropped;
    private final Counter authSuccesses;
    private final Counter authFailures;
    private final Counter chatMessages;
    private final Counter economyTransactions;
    private final Counter wiredExecutions;
    private final Counter gameMatchesStarted;
    private final Counter gameMatchesEnded;
    private final Counter gardenHarvests;

    private final Timer packetProcessingTime;
    private final Timer dbQueryTime;

    public MetricsRegistry() {
        this.registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        Tags commonTags = Tags.of("app", "habnut-emulator");
        registry.config().commonTags(commonTags);

        // JVM metrics
        new ClassLoaderMetrics().bindTo(registry);
        new JvmMemoryMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
        new UptimeMetrics().bindTo(registry);

        // Gauge metrics
        Gauge.builder("habnut.players.connected", connectedPlayers, AtomicInteger::get)
            .description("Number of currently connected players")
            .register(registry);

        Gauge.builder("habnut.rooms.active", activeRooms, AtomicInteger::get)
            .description("Number of rooms with at least one player")
            .register(registry);

        Gauge.builder("habnut.db.pool.active", dbPoolActive, AtomicInteger::get)
            .description("Active database connections")
            .register(registry);

        Gauge.builder("habnut.db.pool.idle", dbPoolIdle, AtomicInteger::get)
            .description("Idle database connections")
            .register(registry);

        // Counter metrics
        packetsReceived     = Counter.builder("habnut.packets.received").register(registry);
        packetsSent         = Counter.builder("habnut.packets.sent").register(registry);
        packetsDropped      = Counter.builder("habnut.packets.dropped").register(registry);
        authSuccesses       = Counter.builder("habnut.auth.success").register(registry);
        authFailures        = Counter.builder("habnut.auth.failure").register(registry);
        chatMessages        = Counter.builder("habnut.chat.messages").register(registry);
        economyTransactions = Counter.builder("habnut.economy.transactions").register(registry);
        wiredExecutions     = Counter.builder("habnut.wired.executions").register(registry);
        gameMatchesStarted  = Counter.builder("habnut.game.matches.started").register(registry);
        gameMatchesEnded    = Counter.builder("habnut.game.matches.ended").register(registry);
        gardenHarvests      = Counter.builder("habnut.garden.harvests").register(registry);

        // Timer metrics
        packetProcessingTime = Timer.builder("habnut.packets.processing.time")
            .description("Time to process a received packet")
            .register(registry);

        dbQueryTime = Timer.builder("habnut.db.query.time")
            .description("Time spent on database queries")
            .register(registry);

        log.info("Metrics registry initialised");
    }

    public String scrape() {
        return registry.scrape();
    }

    public MeterRegistry getRegistry() {
        return registry;
    }

    public void setConnectedPlayers(int count)  { connectedPlayers.set(count); }
    public void setActiveRooms(int count)        { activeRooms.set(count); }
    public void setDbPoolActive(int count)       { dbPoolActive.set(count); }
    public void setDbPoolIdle(int count)         { dbPoolIdle.set(count); }

    public void incrementPacketsReceived()       { packetsReceived.increment(); }
    public void incrementPacketsSent()           { packetsSent.increment(); }
    public void incrementPacketsDropped()        { packetsDropped.increment(); }
    public void incrementAuthSuccess()           { authSuccesses.increment(); }
    public void incrementAuthFailure()           { authFailures.increment(); }
    public void incrementChatMessages()          { chatMessages.increment(); }
    public void incrementEconomyTransactions()   { economyTransactions.increment(); }
    public void incrementWiredExecutions()       { wiredExecutions.increment(); }
    public void incrementGameMatchesStarted()    { gameMatchesStarted.increment(); }
    public void incrementGameMatchesEnded()      { gameMatchesEnded.increment(); }
    public void incrementGardenHarvests()        { gardenHarvests.increment(); }

    public Timer.Sample startPacketTimer()       { return Timer.start(registry); }

    public void recordPacketTime(Timer.Sample sample) {
        sample.stop(packetProcessingTime);
    }

    public Timer getDbQueryTimer()               { return dbQueryTime; }
}
