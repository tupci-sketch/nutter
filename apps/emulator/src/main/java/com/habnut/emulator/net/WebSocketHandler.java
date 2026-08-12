package com.habnut.emulator.net;

import com.habnut.emulator.metrics.MetricsRegistry;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public final class WebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final Logger log = LoggerFactory.getLogger(WebSocketHandler.class);

    private final SessionRegistry sessions;
    private final PacketRouter router;
    private final RateLimiter rateLimiter;
    private final MetricsRegistry metrics;

    public WebSocketHandler(SessionRegistry sessions, PacketRouter router,
                             RateLimiter rateLimiter, MetricsRegistry metrics) {
        super(true);
        this.sessions    = sessions;
        this.router      = router;
        this.rateLimiter = rateLimiter;
        this.metrics     = metrics;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        WebSocketSession session = sessions.register(ctx.channel());
        metrics.setConnectedPlayers(sessions.connectedCount());
        log.debug("Client connected: session={} addr={}", session.sessionId, session.remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        WebSocketSession session = SessionRegistry.fromChannel(ctx.channel());
        if (session != null) {
            sessions.remove(session);
            rateLimiter.remove(session.sessionId);
            metrics.setConnectedPlayers(sessions.connectedCount());
            log.debug("Client disconnected: session={}", session.sessionId);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        WebSocketSession session = SessionRegistry.fromChannel(ctx.channel());
        if (session == null) return;

        metrics.incrementPacketsReceived();

        if (!rateLimiter.tryAcquire(session.sessionId, RateLimiter.Bucket.COMBINED)) {
            metrics.incrementPacketsDropped();
            log.debug("Rate limit exceeded for session {}", session.sessionId);
            return;
        }

        var sample = metrics.startPacketTimer();
        try {
            router.dispatch(session, frame.text());
        } finally {
            metrics.recordPacketTime(sample);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idle && idle.state() == IdleState.READER_IDLE) {
            WebSocketSession session = SessionRegistry.fromChannel(ctx.channel());
            long sid = session != null ? session.sessionId : -1;
            log.debug("Idle timeout, closing session {}", sid);
            ctx.close();
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        WebSocketSession session = SessionRegistry.fromChannel(ctx.channel());
        long sid = session != null ? session.sessionId : -1;
        log.warn("Exception in session {}: {}", sid, cause.getMessage());
        ctx.close();
    }
}
