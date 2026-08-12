package com.habnut.emulator.net;

import com.habnut.emulator.config.ServerConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class NettyServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NettyServer.class);

    private final int port;
    private final int maxFrameSize;
    private final int maxAggregatedSize;
    private final Supplier<ChannelHandler> handlerFactory;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public NettyServer(ServerConfig config, Supplier<ChannelHandler> handlerFactory) {
        this.port = config.wsPort;
        this.maxFrameSize = config.wsMaxFrameSize;
        this.maxAggregatedSize = config.wsMaxAggregatedFrameSize;
        this.handlerFactory = handlerFactory;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast("idle", new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS));
                    pipeline.addLast("http-codec", new HttpServerCodec());
                    pipeline.addLast("http-aggregator", new HttpObjectAggregator(maxAggregatedSize));
                    pipeline.addLast("ws-compression", new WebSocketServerCompressionHandler());
                    pipeline.addLast("ws-protocol", new WebSocketServerProtocolHandler(
                        "/ws", null, true, maxFrameSize));
                    pipeline.addLast("ws-handler", handlerFactory.get());
                }
            });

        ChannelFuture future = bootstrap.bind(port).sync();
        serverChannel = future.channel();
        log.info("WebSocket server listening on port {}", port);
    }

    @Override
    public void close() {
        log.info("Shutting down WebSocket server");
        if (serverChannel != null) {
            serverChannel.close().awaitUninterruptibly(5, TimeUnit.SECONDS);
        }
        if (workerGroup != null) workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        if (bossGroup != null)  bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
    }
}
