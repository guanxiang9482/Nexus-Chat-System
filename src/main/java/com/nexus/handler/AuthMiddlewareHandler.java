package com.nexus.handler;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nexus.domain.User;
import com.nexus.repository.UserRepository;
import com.nexus.service.AuthService;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * Pipeline middleware — sits BEFORE ChatDispatchHandler.
 * 
 * Responsibilities:
 *   1. Allow LOGIN and REGISTER commands through untouched
 *      (AuthHandler handles those)
 *   2. For every other command — verify the JWT token
 *   3. If valid — attach the User to the channel and pass along
 *   4. If invalid — reject and tell client to authenticate first
 *
 * ChatDispatchHandler never touches JWT. It just reads the User
 * from the channel attribute and trusts it blindly.
 *
 * @ChannelHandler.Sharable — one instance shared across all channels.
 * Safe because all state lives on the channel, not on this handler.
 */
@ChannelHandler.Sharable
public class AuthMiddlewareHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(AuthMiddlewareHandler.class);

    // Commands that are allowed without authentication
    private static final String CMD_LOGIN    = "LOGIN";
    private static final String CMD_REGISTER = "REGISTER";

    // Token prefix clients send with every authenticated command
    // Protocol: "TOKEN <jwt> /msg hello everyone"
    private static final String TOKEN_PREFIX = "TOKEN ";

    private final AuthService    authService;
    private final UserRepository userRepository;

    public AuthMiddlewareHandler(AuthService authService,
                                  UserRepository userRepository) {
        this.authService    = authService;
        this.userRepository = userRepository;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        String trimmed = msg.trim();

        // ── Allow auth commands through without token check ──────────
        if (trimmed.startsWith(CMD_LOGIN) || trimmed.startsWith(CMD_REGISTER)) {
            ctx.fireChannelRead(msg);   // pass to next handler (AuthHandler)
            return;
        }

        // ── All other commands require a token ────────────────────────
        if (!trimmed.startsWith(TOKEN_PREFIX)) {
            ctx.channel().writeAndFlush(
                "[SERVER] Not authenticated. Please LOGIN first.\n"
            );
            return;
        }

        // ── Extract and verify the token ──────────────────────────────
        // Format: "TOKEN <jwt> <actual command>"
        String withoutPrefix = trimmed.substring(TOKEN_PREFIX.length());
        int    spaceIndex    = withoutPrefix.indexOf(' ');

        if (spaceIndex == -1) {
            // No command after token
            ctx.channel().writeAndFlush("[SERVER] Invalid format.\n");
            return;
        }

        String jwt     = withoutPrefix.substring(0, spaceIndex);
        String command = withoutPrefix.substring(spaceIndex + 1).trim();

        // Verify JWT — returns username if valid, empty if not
        String username = authService.verifyToken(jwt);
        if (username == null) {
            ctx.channel().writeAndFlush(
                "[SERVER] Session expired. Please LOGIN again.\n"
            );
            return;
        }

        // Check if User already attached from a previous message
        // Avoids a DB lookup on every single command
        User existingUser = ctx.channel().attr(ChannelAttributes.USER).get();

        if (existingUser == null) {
            // First authenticated command — load from DB and cache on channel
            Optional<User> userOpt = userRepository.findByUsername(username);

            if (userOpt.isEmpty()) {
                log.warn("JWT valid but user not found in DB: {}", username);
                ctx.channel().writeAndFlush("[SERVER] Authentication error.\n");
                return;
            }

            ctx.channel().attr(ChannelAttributes.USER).set(userOpt.get());
            log.debug("User '{}' authenticated and attached to channel", username);
        }

        // ── Pass the actual command downstream ────────────────────────
        ctx.fireChannelRead(command);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("AuthMiddlewareHandler error", cause);
        ctx.close();
    }
}