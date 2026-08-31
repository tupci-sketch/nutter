package com.habnut.emulator.config;

import java.util.Optional;

public final class ServerConfig {

    public final String dbUrl;
    public final String dbUsername;
    public final String dbPassword;
    public final int dbPoolMinIdle;
    public final int dbPoolMaxSize;
    public final long dbConnectionTimeoutMs;
    public final long dbIdleTimeoutMs;
    public final long dbMaxLifetimeMs;
    public final long dbKeepaliveTimeMs;

    public final String redisHost;
    public final int redisPort;
    public final String redisPassword;
    public final int redisMaxPoolSize;
    public final int redisTimeoutMs;

    public final int wsPort;
    public final int healthPort;
    public final int metricsPort;
    public final int wsMaxFrameSize;
    public final int wsMaxAggregatedFrameSize;

    public final String worldId;
    public final int sessionTicketTtlSeconds;

    public final boolean devMode;

    private ServerConfig(Builder b) {
        this.dbUrl = b.dbUrl;
        this.dbUsername = b.dbUsername;
        this.dbPassword = b.dbPassword;
        this.dbPoolMinIdle = b.dbPoolMinIdle;
        this.dbPoolMaxSize = b.dbPoolMaxSize;
        this.dbConnectionTimeoutMs = b.dbConnectionTimeoutMs;
        this.dbIdleTimeoutMs = b.dbIdleTimeoutMs;
        this.dbMaxLifetimeMs = b.dbMaxLifetimeMs;
        this.dbKeepaliveTimeMs = b.dbKeepaliveTimeMs;
        this.redisHost = b.redisHost;
        this.redisPort = b.redisPort;
        this.redisPassword = b.redisPassword;
        this.redisMaxPoolSize = b.redisMaxPoolSize;
        this.redisTimeoutMs = b.redisTimeoutMs;
        this.wsPort = b.wsPort;
        this.healthPort = b.healthPort;
        this.metricsPort = b.metricsPort;
        this.wsMaxFrameSize = b.wsMaxFrameSize;
        this.wsMaxAggregatedFrameSize = b.wsMaxAggregatedFrameSize;
        this.worldId = b.worldId;
        this.sessionTicketTtlSeconds = b.sessionTicketTtlSeconds;
        this.devMode = b.devMode;
    }

    public static ServerConfig fromEnvironment() {
        return new Builder()
            .dbUrl(required("DB_URL"))
            .dbUsername(required("DB_USERNAME"))
            .dbPassword(required("DB_PASSWORD"))
            .dbPoolMinIdle(intOpt("DB_POOL_MIN_IDLE", 5))
            .dbPoolMaxSize(intOpt("DB_POOL_MAX_POOL_SIZE", 20))
            .dbConnectionTimeoutMs(longOpt("DB_CONNECTION_TIMEOUT_MS", 3000))
            .dbIdleTimeoutMs(longOpt("DB_IDLE_TIMEOUT_MS", 600000))
            .dbMaxLifetimeMs(longOpt("DB_MAX_LIFETIME_MS", 1800000))
            .dbKeepaliveTimeMs(longOpt("DB_KEEPALIVE_TIME_MS", 60000))
            .redisHost(opt("REDIS_HOST", "localhost"))
            .redisPort(intOpt("REDIS_PORT", 6379))
            .redisPassword(opt("REDIS_PASSWORD", null))
            .redisMaxPoolSize(intOpt("REDIS_MAX_POOL_SIZE", 20))
            .redisTimeoutMs(intOpt("REDIS_TIMEOUT_MS", 2000))
            .wsPort(intOpt("WS_PORT", 3000))
            .healthPort(intOpt("HEALTH_PORT", 3001))
            .metricsPort(intOpt("METRICS_PORT", 3002))
            .wsMaxFrameSize(intOpt("WS_MAX_FRAME_SIZE", 65536))
            .wsMaxAggregatedFrameSize(intOpt("WS_MAX_AGGREGATED_FRAME_SIZE", 524288))
            .worldId(opt("WORLD_ID", "classic"))
            .sessionTicketTtlSeconds(intOpt("SESSION_TICKET_TTL_SECONDS", 300))
            .devMode(boolOpt("DEV_MODE", false))
            .build();
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable not set: " + key);
        }
        return value;
    }

    private static String opt(String key, String defaultValue) {
        return Optional.ofNullable(System.getenv(key)).filter(s -> !s.isBlank()).orElse(defaultValue);
    }

    private static int intOpt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) return defaultValue;
        return Integer.parseInt(value.trim());
    }

    private static long longOpt(String key, long defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) return defaultValue;
        return Long.parseLong(value.trim());
    }

    private static boolean boolOpt(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) return defaultValue;
        return Boolean.parseBoolean(value.trim());
    }

    public static final class Builder {
        String dbUrl, dbUsername, dbPassword;
        int dbPoolMinIdle = 5, dbPoolMaxSize = 20;
        long dbConnectionTimeoutMs = 3000, dbIdleTimeoutMs = 600000,
             dbMaxLifetimeMs = 1800000, dbKeepaliveTimeMs = 60000;
        String redisHost = "localhost";
        int redisPort = 6379;
        String redisPassword = null;
        int redisMaxPoolSize = 20, redisTimeoutMs = 2000;
        int wsPort = 3000, healthPort = 3001, metricsPort = 3002;
        int wsMaxFrameSize = 65536, wsMaxAggregatedFrameSize = 524288;
        String worldId = "classic";
        int sessionTicketTtlSeconds = 300;
        boolean devMode = false;

        public Builder dbUrl(String v)                      { dbUrl = v; return this; }
        public Builder dbUsername(String v)                 { dbUsername = v; return this; }
        public Builder dbPassword(String v)                 { dbPassword = v; return this; }
        public Builder dbPoolMinIdle(int v)                 { dbPoolMinIdle = v; return this; }
        public Builder dbPoolMaxSize(int v)                 { dbPoolMaxSize = v; return this; }
        public Builder dbConnectionTimeoutMs(long v)        { dbConnectionTimeoutMs = v; return this; }
        public Builder dbIdleTimeoutMs(long v)              { dbIdleTimeoutMs = v; return this; }
        public Builder dbMaxLifetimeMs(long v)              { dbMaxLifetimeMs = v; return this; }
        public Builder dbKeepaliveTimeMs(long v)            { dbKeepaliveTimeMs = v; return this; }
        public Builder redisHost(String v)                  { redisHost = v; return this; }
        public Builder redisPort(int v)                     { redisPort = v; return this; }
        public Builder redisPassword(String v)              { redisPassword = v; return this; }
        public Builder redisMaxPoolSize(int v)              { redisMaxPoolSize = v; return this; }
        public Builder redisTimeoutMs(int v)                { redisTimeoutMs = v; return this; }
        public Builder wsPort(int v)                        { wsPort = v; return this; }
        public Builder healthPort(int v)                    { healthPort = v; return this; }
        public Builder metricsPort(int v)                   { metricsPort = v; return this; }
        public Builder wsMaxFrameSize(int v)                { wsMaxFrameSize = v; return this; }
        public Builder wsMaxAggregatedFrameSize(int v)      { wsMaxAggregatedFrameSize = v; return this; }
        public Builder worldId(String v)                    { worldId = v; return this; }
        public Builder sessionTicketTtlSeconds(int v)       { sessionTicketTtlSeconds = v; return this; }
        public Builder devMode(boolean v)                   { devMode = v; return this; }

        public ServerConfig build() {
            if (dbUrl == null) throw new IllegalStateException("dbUrl is required");
            if (dbUsername == null) throw new IllegalStateException("dbUsername is required");
            if (dbPassword == null) throw new IllegalStateException("dbPassword is required");
            return new ServerConfig(this);
        }
    }
}
