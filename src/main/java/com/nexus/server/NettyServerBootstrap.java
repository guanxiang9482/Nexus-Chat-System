package com.nexus.server;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nexus.config.ServerConfig;
import com.nexus.handler.AuthMiddlewareHandler;
import com.nexus.handler.ChatDispatchHandler;
import com.nexus.handler.LoginCommandHandler;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * Builds and starts the Netty server.
 * Owns boss/worker event loop groups.
 * Implements AutoCloseable for clean shutdown.
 */
public class NettyServerBootstrap implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NettyServerBootstrap.class);

    private final ServerConfig           serverConfig;
    private final LoginCommandHandler    loginCommandHandler;
    private final AuthMiddlewareHandler  authMiddlewareHandler;
    private final ChatDispatchHandler    chatDispatchHandler;

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;

    // Tracks all connected channels for clean shutdown
    private final ChannelGroup allChannels =
            new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    // ── M1 compatibility constructor ─────────────────────────────────
    public NettyServerBootstrap(ServerConfig serverConfig) {
        this(serverConfig, null, null, null);
    }

    // ── M2 compatibility constructor ─────────────────────────────────
    public NettyServerBootstrap(ServerConfig serverConfig,
                                LoginCommandHandler loginCommandHandler) {
        this(serverConfig, loginCommandHandler, null, null);
    }

    // ── M3 primary constructor ────────────────────────────────────────
    public NettyServerBootstrap(ServerConfig serverConfig,
                                LoginCommandHandler loginCommandHandler,
                                AuthMiddlewareHandler authMiddlewareHandler,
                                ChatDispatchHandler chatDispatchHandler) {
        this.serverConfig          = serverConfig;
        this.loginCommandHandler   = loginCommandHandler;
        this.authMiddlewareHandler = authMiddlewareHandler;
        this.chatDispatchHandler   = chatDispatchHandler;
    }

    // ----------------------------------------------------------------
    // start()
    // ----------------------------------------------------------------
    public void start() throws InterruptedException {
        bossGroup   = new NioEventLoopGroup(serverConfig.getBossThreads());
        workerGroup = new NioEventLoopGroup(serverConfig.getWorkerThreads());

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                 .channel(NioServerSocketChannel.class)
                 .childHandler(new ChannelInitializer<SocketChannel>() {
                     @Override
                     protected void initChannel(SocketChannel ch) {
                         buildPipeline(ch.pipeline());
                         allChannels.add(ch);
                     }
                 })
                 .option(ChannelOption.SO_BACKLOG, 128)
                 .childOption(ChannelOption.SO_KEEPALIVE, true);

        ChannelFuture future = bootstrap.bind(serverConfig.getPort()).sync();
        log.info("Nexus server started on port {}", serverConfig.getPort());
        future.channel().closeFuture().sync();
    }

    // ----------------------------------------------------------------
    // buildPipeline()
    // Order is load-bearing — do not reorder.
    // ----------------------------------------------------------------
    private void buildPipeline(ChannelPipeline pipeline) {
        // ── Inbound: raw bytes → String ───────────────────────────────
        pipeline.addLast("frameDecoder",
            new LengthFieldBasedFrameDecoder(
                serverConfig.getMaxFrameLengthBytes(), 0, 4, 0, 4
            )
        );
        pipeline.addLast("framePrepender",
            new LengthFieldPrepender(4)
        );
        pipeline.addLast("stringDecoder",
            new StringDecoder(StandardCharsets.UTF_8)
        );
        pipeline.addLast("stringEncoder",
            new StringEncoder(StandardCharsets.UTF_8)
        );

        // ── Idle connection handler ───────────────────────────────────
        pipeline.addLast("idleHandler",
            new IdleStateHandler(
                serverConfig.getIdleTimeoutSeconds(), 0, 0, TimeUnit.SECONDS
            )
        );

        // ── Auth: LOGIN / REGISTER ────────────────────────────────────
        if (loginCommandHandler != null) {
            pipeline.addLast("authHandler",
                buildAuthHandler()
            );
        }

        // ── Auth middleware: JWT verification ─────────────────────────
        if (authMiddlewareHandler != null) {
            pipeline.addLast("authMiddleware", authMiddlewareHandler);
        }

        // ── Chat dispatch: /join /leave /msg /list /create ────────────
        if (chatDispatchHandler != null) {
            pipeline.addLast("chatDispatch", chatDispatchHandler);
        }
    }

    // ----------------------------------------------------------------
    // buildAuthHandler()
    // Wraps LoginCommandHandler in a Netty adapter.
    // Keeps LoginCommandHandler free of Netty imports.
    // ----------------------------------------------------------------
    private ChannelHandler buildAuthHandler() {
        LoginCommandHandler handler = this.loginCommandHandler;

        return new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (msg instanceof String command) {
                    String trimmed = command.trim().toUpperCase();
                    if (trimmed.startsWith("LOGIN") ||
                        trimmed.startsWith("REGISTER")) {
                        handler.handle(command ,ctx);
                        return;
                    }
                }
                // Not an auth command — pass to next handler
                ctx.fireChannelRead(msg);
            }
        };
    }

    // ----------------------------------------------------------------
    // close() — called by shutdown hook in NexusApplication
    // ----------------------------------------------------------------
    @Override
    public void close() {
        log.info("Shutting down Nexus server...");
        allChannels.close().awaitUninterruptibly();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        if (bossGroup   != null) bossGroup.shutdownGracefully();
        log.info("Nexus server shut down cleanly.");
    }
}