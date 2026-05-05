package com.nexus.server;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nexus.config.ServerConfig;
import com.nexus.handler.EchoChannelHandler;
import com.nexus.handler.LoginCommandHandler;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;

public final class NettyServerBootstrap implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NettyServerBootstrap.class);

    private final ServerConfig        config;
    private final EchoChannelHandler  echoHandler;
    private final LoginCommandHandler loginHandler;

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private Channel           serverChannel;

    /**
     * Milestone 1 constructor — no auth, echo only.
     * Retained so existing tests that only test the pipeline skeleton
     * do not need to be updated.
     */
    public NettyServerBootstrap(ServerConfig config) {
        this(config, null);
    }

    /**
     * Milestone 2 constructor — full auth pipeline.
     * loginHandler may be null during unit tests that only verify
     * Netty pipeline mechanics without a real AuthService.
     */
    public NettyServerBootstrap(ServerConfig config, LoginCommandHandler loginHandler) {
        this.config       = config;
        this.loginHandler = loginHandler;
        this.echoHandler  = new EchoChannelHandler();
    }

    public void start() throws InterruptedException {
        bossGroup   = new NioEventLoopGroup(config.getBossThreads());
        workerGroup = new NioEventLoopGroup(config.getWorkerThreads());

        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        buildPipeline(ch.pipeline());
                    }
                });

            ChannelFuture future = bootstrap.bind(config.getPort()).sync();
            serverChannel = future.channel();
            log.info("Nexus server started on port {}", config.getPort());
            serverChannel.closeFuture().sync();

        } finally {
            close();
        }
    }

    /**
     * Pipeline construction — Chain of Responsibility.
     *
     * Handler insertion order for Milestone 2:
     *   frameDecoder   → reconstruct complete frames from TCP stream
     *   framePrepender → outbound: prepend 4-byte length header
     *   stringDecoder  → inbound:  ByteBuf → String
     *   stringEncoder  → outbound: String → ByteBuf
     *   idleHandler    → fire IdleStateEvent after N seconds silence
     *   authHandler    → NEW: intercept LOGIN / REGISTER commands
     *   echoHandler    → fallback: echo anything auth didn't consume
     *
     * The authHandler sits before echoHandler so LOGIN and REGISTER
     * commands are consumed before the echo handler sees them.
     * In Milestone 3, echoHandler is replaced by ChatDispatchHandler.
     */
    private void buildPipeline(ChannelPipeline pipeline) {
        pipeline
            .addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
                config.getMaxFrameLengthBytes(), 0, 4, 0, 4))
            .addLast("framePrepender", new LengthFieldPrepender(4))
            .addLast("stringDecoder", new StringDecoder(StandardCharsets.UTF_8))
            .addLast("stringEncoder", new StringEncoder(StandardCharsets.UTF_8))
            .addLast("idleHandler",   new IdleStateHandler(
                config.getIdleTimeoutSeconds(), 0, 0, TimeUnit.SECONDS))
            .addLast("authHandler",   buildAuthHandler())
            .addLast("echoHandler",   echoHandler);
    }

    /**
     * Wraps LoginCommandHandler (a plain Java class) in a Netty
     * ChannelInboundHandlerAdapter so it can sit in the pipeline.
     *
     * Why a wrapper instead of making LoginCommandHandler extend
     * ChannelInboundHandlerAdapter directly?
     * LoginCommandHandler is a pure business-logic class — it has no
     * dependency on Netty types. Keeping it free of Netty coupling means
     * it can be unit-tested without an EmbeddedChannel, and swapped for
     * a different transport (gRPC, WebSocket) with zero changes to the
     * handler itself. The wrapper is the only place Netty and auth logic
     * meet.
     *
     * Returns a no-op pass-through handler when loginHandler is null
     * (Milestone 1 / skeleton-only mode).
     */
    private ChannelHandler buildAuthHandler() {
        return new io.netty.channel.ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (loginHandler != null && msg instanceof String message) {
                    boolean consumed = loginHandler.handle(message, ctx);
                    if (consumed) return; // do not pass to next handler
                }
                ctx.fireChannelRead(msg); // pass to echoHandler
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                log.error("Auth handler exception: {}", cause.getMessage(), cause);
                ctx.close();
            }
        };
    }

    @Override
    public void close() {
        log.info("Shutting down Nexus server...");
        if (workerGroup != null) workerGroup.shutdownGracefully();
        if (bossGroup   != null) bossGroup.shutdownGracefully();
        log.info("Nexus server stopped.");
    }
}