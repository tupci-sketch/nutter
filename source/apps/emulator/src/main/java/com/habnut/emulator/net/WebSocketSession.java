package com.habnut.emulator.net;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class WebSocketSession {

    private static final AtomicLong ID_SEQ = new AtomicLong(1);

    public final long sessionId;
    public final Channel channel;

    private final AtomicReference<Long> authenticatedUserId = new AtomicReference<>(null);
    private final AtomicReference<String> worldId = new AtomicReference<>(null);

    public WebSocketSession(Channel channel) {
        this.sessionId = ID_SEQ.getAndIncrement();
        this.channel = channel;
    }

    public void send(String json) {
        if (channel.isActive()) {
            channel.writeAndFlush(new TextWebSocketFrame(json));
        }
    }

    public void close() {
        channel.close();
    }

    public void closeAfterSend(String json) {
        if (channel.isActive()) {
            channel.writeAndFlush(new TextWebSocketFrame(json))
                   .addListener(ChannelFutureListener.CLOSE);
        }
    }

    public boolean isAuthenticated() {
        return authenticatedUserId.get() != null;
    }

    public Long getUserId() {
        return authenticatedUserId.get();
    }

    public void authenticate(long userId, String world) {
        authenticatedUserId.set(userId);
        worldId.set(world);
    }

    public void deauthenticate() {
        authenticatedUserId.set(null);
        worldId.set(null);
    }

    public String getWorldId() {
        return worldId.get();
    }

    public String remoteAddress() {
        return channel.remoteAddress() != null ? channel.remoteAddress().toString() : "unknown";
    }
}
