package com.habnut.emulator;

import com.habnut.emulator.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        ServerConfig config;
        try {
            config = ServerConfig.fromEnvironment();
        } catch (Exception e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }

        ServerBootstrap bootstrap = new ServerBootstrap(config);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received");
            bootstrap.stop();
        }, "shutdown-hook"));

        try {
            bootstrap.start();
            bootstrap.awaitShutdown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Fatal startup error", e);
            System.exit(1);
        }
    }
}
