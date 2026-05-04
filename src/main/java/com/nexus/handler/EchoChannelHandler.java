package com.nexus.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * Echo handler — Milestone 1 proof-of-life.
 *
 * Pattern: Template Method via SimpleChannelInboundHandler<String>.
 * The superclass defines the pipeline algorithm (acquire → process → release).
 * We override channelRead0() with our variant logic only.
 *
 * @ChannelHandler.Sharable: marks this instance as safe to share across
 * multiple channels. Requirements: no per-channel mutable state in this class.
 * If you add per-channel state later, remove this annotation and use
 * ChannelLocal<T> or a per-channel attribute map instead.
 */
@ChannelHandler.Sharable
public final class EchoChannelHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(EchoChannelHandler.class);

    // Stateless — safe to share. No instance fields.

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("Client connected: {}", ctx.channel().remoteAddress());
        ctx.writeAndFlush("NEXUS v1.0 — connection established\n");
    }

    /**
     * Core logic: echo the message back to the sender.
     * This method is invoked by the superclass after safe ByteBuf acquisition.
     * Never call ReferenceCountUtil.release() here — the superclass handles it.
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String message) {
        log.debug("Received from {}: '{}'", ctx.channel().remoteAddress(), message);
        // Echo back with a server prefix — proof the full pipeline works
        ctx.writeAndFlush("[ECHO] " + message + "\n");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("Client disconnected: {}", ctx.channel().remoteAddress());
    }

    /**
     * Handles IdleStateEvent fired by IdleStateHandler.
     * In production this becomes the heartbeat enforcement point.
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            log.warn("Idle timeout — closing channel: {}", ctx.channel().remoteAddress());
            ctx.writeAndFlush("[SERVER] Connection closed due to inactivity.\n")
               .addListener(f -> ctx.close());
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    /**
     * All unhandled exceptions surface here. Log, then close the channel.
     * Never silently swallow — that causes resource leaks.
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Unhandled exception on channel {}: {}",
                  ctx.channel().remoteAddress(), cause.getMessage(), cause);
        ctx.close();
    }
}