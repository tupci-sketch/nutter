package com.habnut.emulator.net;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    public static final AttributeKey<WebSocketSession> SESSION_KEY =
        AttributeKey.valueOf("habnut.session");

    private final ConcurrentHashMap<Long, WebSocketSession> bySessionId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, WebSocketSession> byUserId    = new ConcurrentHashMap<>();

    public WebSocketSession register(Channel channel) {
        WebSocketSession session = new WebSocketSession(channel);
        channel.attr(SESSION_KEY).set(session);
        bySessionId.put(session.sessionId, session);
        log.debug("Session registered: id={} addr={}", session.sessionId, session.remoteAddress());
        return session;
    }

    public void onAuthenticated(WebSocketSession session, long userId) {
        byUserId.put(userId, session);
    }

    public void remove(WebSocketSession session) {
        bySessionId.remove(session.sessionId);
        Long uid = session.getUserId();
        if (uid != null) byUserId.remove(uid, session);
        log.debug("Session removed: id={}", session.sessionId);
    }

    public Optional<WebSocketSession> byUserId(long userId) {
        return Optional.ofNullable(byUserId.get(userId));
    }

    public Optional<WebSocketSession> bySessionId(long sessionId) {
        return Optional.ofNullable(bySessionId.get(sessionId));
    }

    public Collection<WebSocketSession> all() {
        return bySessionId.values();
    }

    public int connectedCount() {
        return bySessionId.size();
    }

    public int authenticatedCount() {
        return byUserId.size();
    }

    public static WebSocketSession fromChannel(Channel channel) {
        return channel.attr(SESSION_KEY).get();
    }
}
