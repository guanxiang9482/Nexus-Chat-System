package com.nexus.server;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nexus.config.ServerConfig;
import com.nexus.handler.EchoChannelHandler;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
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

/**
 * Configures and starts the Netty server.
 *
 * Threading model (critical — never violate this):
 *   bossGroup   : 1 thread — accepts TCP connections, hands them to workers.
 *   workerGroup : N threads — each owns a set of channels, handles all I/O
 *                 for those channels. Must NEVER block (no DB calls, no sleep).
 *
 * Pipeline construction is isolated in buildPipeline() — a factory method that
 * builds the Chain of Responsibility for each new channel.
 *
 * Implements AutoCloseable so it shuts down cleanly in a try-with-resources block.
 */
public final class NettyServerBootstrap implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NettyServerBootstrap.class);

    private final ServerConfig config;
    private final EchoChannelHandler echoHandler;

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private Channel serverChannel;

    public NettyServerBootstrap(ServerConfig config) {
        this.config      = config;
        // Shared (Sharable) — one instance, many channels
        this.echoHandler = new EchoChannelHandler();
    }

    /**
     * Starts the server and blocks until the server channel is closed.
     * Call this from the main thread.
     */
    public void start() throws InterruptedException {
        bossGroup   = new NioEventLoopGroup(config.getBossThreads());
        workerGroup = new NioEventLoopGroup(config.getWorkerThreads());

        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                // Dev-time pipeline logger — remove or set to WARN in production
                .handler(new LoggingHandler(LogLevel.INFO))
                // SO_BACKLOG: max queued connections before the OS refuses new ones
                .option(ChannelOption.SO_BACKLOG, 128)
                // Child options apply to each accepted SocketChannel
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

            // Block main thread until the server channel closes
            serverChannel.closeFuture().sync();

        } finally {
            close();
        }
    }

    /**
     * Pipeline factory — Chain of Responsibility.
     *
     * Order is load-bearing:
     *   1. LengthFieldBasedFrameDecoder — reconstructs complete frames from TCP stream.
     *      TCP is a stream protocol; bytes do not arrive in discrete message units.
     *      This handler buffers bytes until a full frame is assembled.
     *   2. LengthFieldPrepender       — outbound: prepends a 4-byte length header.
     *   3. StringDecoder              — inbound: ByteBuf → String (UTF-8).
     *   4. StringEncoder              — outbound: String → ByteBuf (UTF-8).
     *   5. IdleStateHandler           — fires IdleStateEvent after N seconds of silence.
     *   6. EchoChannelHandler         — business logic (our code).
     *
     * Future handlers (Auth, Dispatch) will be inserted between IdleStateHandler
     * and EchoChannelHandler — zero changes to existing handlers.
     */
    private void buildPipeline(ChannelPipeline pipeline) {
        pipeline
            // --- Framing (inbound) ---
            .addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
                config.getMaxFrameLengthBytes(), // maxFrameLength
                0,                               // lengthFieldOffset
                4,                               // lengthFieldLength (4-byte int)
                0,                               // lengthAdjustment
                4                                // initialBytesToStrip (strip the header)
            ))
            // --- Framing (outbound) ---
            .addLast("framePrepender", new LengthFieldPrepender(4))
            // --- String codec ---
            .addLast("stringDecoder", new StringDecoder(StandardCharsets.UTF_8))
            .addLast("stringEncoder", new StringEncoder(StandardCharsets.UTF_8))
            // --- Idle detection ---
            .addLast("idleHandler", new IdleStateHandler(
                config.getIdleTimeoutSeconds(), 0, 0, TimeUnit.SECONDS
            ))
            // --- Business logic ---
            .addLast("echoHandler", echoHandler);
    }

    /**
     * Graceful shutdown — drains in-flight work before releasing threads.
     * Called automatically by try-with-resources or explicitly on JVM shutdown.
     */
    @Override
    public void close() {
        log.info("Shutting down Nexus server...");
        if (workerGroup != null) workerGroup.shutdownGracefully();
        if (bossGroup   != null) bossGroup.shutdownGracefully();
        log.info("Nexus server stopped.");
    }
}