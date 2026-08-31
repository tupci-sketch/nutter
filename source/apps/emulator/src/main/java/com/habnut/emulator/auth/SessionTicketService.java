package com.habnut.emulator.auth;

import com.habnut.emulator.config.ServerConfig;
import com.habnut.emulator.redis.RedisManager;

import java.security.SecureRandom;
import java.util.Base64;

public final class SessionTicketService {

    private static final SecureRandom RNG = new SecureRandom();

    private final RedisManager redis;
    private final int ttlSeconds;

    public SessionTicketService(RedisManager redis, ServerConfig config) {
        this.redis      = redis;
        this.ttlSeconds = config.sessionTicketTtlSeconds;
    }

    public String issue(long userId, String worldId) {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String key = RedisManager.KEY_TICKET + ticket;
        redis.setex(key, ttlSeconds, userId + ":" + worldId);
        return ticket;
    }

    public record TicketClaim(long userId, String worldId) {}

    public TicketClaim consume(String ticket) {
        String key = RedisManager.KEY_TICKET + ticket;
        String value = redis.get(key);
        if (value == null) return null;
        redis.del(key);
        String[] parts = value.split(":", 2);
        if (parts.length != 2) return null;
        return new TicketClaim(Long.parseLong(parts[0]), parts[1]);
    }
}
