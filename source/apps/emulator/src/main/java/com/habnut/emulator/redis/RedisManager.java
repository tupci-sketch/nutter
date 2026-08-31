package com.habnut.emulator.redis;

import com.habnut.emulator.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;

public final class RedisManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RedisManager.class);

    private final JedisPool pool;

    public static final String KEY_SESSION    = "habnut:session:";
    public static final String KEY_TICKET     = "habnut:ticket:";
    public static final String KEY_ONLINE     = "habnut:online";
    public static final String KEY_RATE_LIMIT = "habnut:ratelimit:";
    public static final String KEY_WIRED_SIGNAL = "habnut:wired:signal:";

    public RedisManager(ServerConfig config) {
        log.info("Initialising Redis connection pool ({}:{})", config.redisHost, config.redisPort);

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.redisMaxPoolSize);
        poolConfig.setMaxIdle(config.redisMaxPoolSize / 2);
        poolConfig.setMinIdle(2);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setMaxWait(Duration.ofMillis(config.redisTimeoutMs));

        if (config.redisPassword != null && !config.redisPassword.isBlank()) {
            this.pool = new JedisPool(poolConfig, config.redisHost, config.redisPort,
                config.redisTimeoutMs, config.redisPassword);
        } else {
            this.pool = new JedisPool(poolConfig, config.redisHost, config.redisPort,
                config.redisTimeoutMs);
        }

        // Verify connectivity
        try (Jedis jedis = pool.getResource()) {
            String pong = jedis.ping();
            log.info("Redis connected: {}", pong);
        }
    }

    public Jedis getResource() {
        return pool.getResource();
    }

    public void setex(String key, long ttlSeconds, String value) {
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(key, ttlSeconds, value);
        }
    }

    public String get(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(key);
        }
    }

    public void del(String key) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(key);
        }
    }

    public boolean exists(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.exists(key);
        }
    }

    public long incr(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.incr(key);
        }
    }

    public void expire(String key, long ttlSeconds) {
        try (Jedis jedis = pool.getResource()) {
            jedis.expire(key, ttlSeconds);
        }
    }

    public void sadd(String key, String member) {
        try (Jedis jedis = pool.getResource()) {
            jedis.sadd(key, member);
        }
    }

    public void srem(String key, String member) {
        try (Jedis jedis = pool.getResource()) {
            jedis.srem(key, member);
        }
    }

    public long scard(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.scard(key);
        }
    }

    public boolean isHealthy() {
        try (Jedis jedis = pool.getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            log.warn("Redis health check failed", e);
            return false;
        }
    }

    @Override
    public void close() {
        log.info("Closing Redis connection pool");
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }
}
