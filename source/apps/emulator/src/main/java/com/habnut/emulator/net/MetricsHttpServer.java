package com.habnut.emulator.net;

import com.habnut.emulator.config.ServerConfig;
import com.habnut.emulator.metrics.MetricsRegistry;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public final class MetricsHttpServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MetricsHttpServer.class);

    private final HttpServer server;

    public MetricsHttpServer(ServerConfig config, MetricsRegistry metrics) throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.metricsPort), 0);
        server.setExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "metrics-server");
            t.setDaemon(true);
            return t;
        }));

        server.createContext("/metrics", exchange -> {
            String body = metrics.scrape();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
    }

    public void start() {
        server.start();
        log.info("Metrics server listening on port {}", server.getAddress().getPort());
    }

    @Override
    public void close() {
        log.info("Shutting down metrics server");
        server.stop(2);
    }
}
