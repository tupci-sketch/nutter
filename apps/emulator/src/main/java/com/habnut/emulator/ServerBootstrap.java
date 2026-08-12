package com.habnut.emulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.habnut.emulator.config.ServerConfig;
import com.habnut.emulator.db.DatabaseManager;
import com.habnut.emulator.db.FlywayRunner;
import com.habnut.emulator.metrics.MetricsRegistry;
import com.habnut.emulator.net.*;
import com.habnut.emulator.redis.RedisManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ServerBootstrap.class);

    private final ServerConfig config;
    private DatabaseManager db;
    private RedisManager redis;
    private MetricsRegistry metrics;
    private SessionRegistry sessions;
    private MetricsHttpServer metricsServer;
    private HealthServer healthServer;
    private NettyServer nettyServer;
    private ScheduledExecutorService metricsPoller;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public ServerBootstrap(ServerConfig config) {
        this.config = config;
    }

    public void start() throws Exception {
        log.info("=== Habnut Emulator starting (worldId={}, devMode={}) ===",
            config.worldId, config.devMode);

        // 1. Database
        db = new DatabaseManager(config);
        FlywayRunner.migrate(db.getDataSource());

        // 2. Redis
        redis = new RedisManager(config);

        // 3. Metrics registry
        metrics = new MetricsRegistry();

        // 4. Session registry
        sessions = new SessionRegistry();

        // 5. Metrics HTTP server
        metricsServer = new MetricsHttpServer(config, metrics);
        metricsServer.start();

        // 6. Health HTTP server
        healthServer = new HealthServer(config, db, redis, sessions);
        healthServer.start();

        // 7. Packet router and WebSocket server
        ObjectMapper mapper = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        PacketRouter router = buildRouter(mapper);
        RateLimiter rateLimiter = new RateLimiter();

        WebSocketHandler handler = new WebSocketHandler(sessions, router, rateLimiter, metrics);

        nettyServer = new NettyServer(config, () -> handler);
        nettyServer.start();

        // 8. Poll DB pool metrics into gauges every 5 s
        metricsPoller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-poller");
            t.setDaemon(true);
            return t;
        });
        metricsPoller.scheduleAtFixedRate(this::pollDbMetrics, 5, 5, TimeUnit.SECONDS);

        running.set(true);
        log.info("=== Habnut Emulator ready ===");
    }

    private PacketRouter buildRouter(ObjectMapper mapper) {
        PacketRouter router = new PacketRouter(mapper);
        // Domain handlers are registered in Phase 4+ as each subsystem is added.
        // System ping for basic connectivity checks.
        router.register("system.ping", (session, payload) -> {
            session.send(router.buildPacket("system.pong", java.util.Map.of("ts", System.currentTimeMillis())));
        });
        return router;
    }

    private void pollDbMetrics() {
        try {
            metrics.setDbPoolActive(db.getActiveConnections());
            metrics.setDbPoolIdle(db.getIdleConnections());
        } catch (Exception e) {
            log.warn("Failed to poll DB pool metrics", e);
        }
    }

    public void awaitShutdown() throws InterruptedException {
        shutdownLatch.await();
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        log.info("Graceful shutdown initiated");

        if (metricsPoller != null) metricsPoller.shutdownNow();
        if (nettyServer != null) nettyServer.close();
        if (healthServer != null) healthServer.close();
        if (metricsServer != null) metricsServer.close();

        // Disconnect all sessions
        if (sessions != null) {
            sessions.all().forEach(WebSocketSession::close);
        }

        if (redis != null) redis.close();
        if (db != null) db.close();

        log.info("Habnut Emulator stopped");
        shutdownLatch.countDown();
    }
}
