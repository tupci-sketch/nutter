package com.habnut.emulator.net;

import com.habnut.emulator.config.ServerConfig;
import com.habnut.emulator.db.DatabaseManager;
import com.habnut.emulator.redis.RedisManager;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public final class HealthServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HealthServer.class);

    private final HttpServer server;

    public HealthServer(ServerConfig config, DatabaseManager db, RedisManager redis,
                        SessionRegistry sessions) throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.healthPort), 0);
        server.setExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "health-server");
            t.setDaemon(true);
            return t;
        }));

        server.createContext("/health", exchange -> {
            boolean dbOk    = db.isHealthy();
            boolean redisOk = redis.isHealthy();
            boolean healthy = dbOk && redisOk;

            String body = String.format(
                "{\"status\":\"%s\",\"db\":%b,\"redis\":%b,\"connections\":%d}",
                healthy ? "UP" : "DOWN", dbOk, redisOk, sessions.connectedCount());

            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            int statusCode = healthy ? 200 : 503;
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
    }

    public void start() {
        server.start();
        log.info("Health server listening on port {}", server.getAddress().getPort());
    }

    @Override
    public void close() {
        log.info("Shutting down health server");
        server.stop(2);
    }
}
