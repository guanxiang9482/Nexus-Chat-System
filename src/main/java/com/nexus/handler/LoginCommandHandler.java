package com.nexus.handler;

import com.nexus.exception.AuthException;
import com.nexus.exception.DuplicateUserException;
import com.nexus.service.AuthService;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * Server-side handler for LOGIN and REGISTER commands.
 *
 * CRITICAL threading rule: bcrypt takes ~250ms. This handler immediately
 * submits work to authExecutor and returns — never blocking the Netty worker.
 * The response is written back via ctx.writeAndFlush() from the executor thread,
 * which is safe because Channel.writeAndFlush() is thread-safe in Netty.
 */
public final class LoginCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginCommandHandler.class);

    private final AuthService    authService;
    private final ExecutorService authExecutor; // dedicated thread pool for bcrypt

    public LoginCommandHandler(AuthService authService, ExecutorService authExecutor) {
        this.authService  = authService;
        this.authExecutor = authExecutor;
    }

    /**
     * Called from the pipeline's channelRead0().
     * Returns true if the command was consumed, false to pass along the chain.
     */
    public boolean handle(String message, ChannelHandlerContext ctx) {
        if (message.startsWith("LOGIN ")) {
            handleLogin(message, ctx);
            return true;
        }
        if (message.startsWith("REGISTER ")) {
            handleRegister(message, ctx);
            return true;
        }
        return false;
    }

    private void handleLogin(String message, ChannelHandlerContext ctx) {
        String[] parts = message.split("\\s+", 3);
        if (parts.length < 3) {
            ctx.writeAndFlush("AUTH_FAIL Usage: LOGIN <username> <password>\n");
            return;
        }
        String username = parts[1];
        String password = parts[2];

        // Submit bcrypt work off the Netty thread
        authExecutor.submit(() -> {
            try {
                String token = authService.login(username, password);
                ctx.writeAndFlush("AUTH_OK " + token + "\n");
                log.info("Login success for '{}' on channel {}", username,
                         ctx.channel().remoteAddress());
            } catch (AuthException e) {
                ctx.writeAndFlush("AUTH_FAIL " + e.getMessage() + "\n");
            } catch (Exception e) {
                log.error("Login handler error: {}", e.getMessage(), e);
                ctx.writeAndFlush("AUTH_FAIL Internal server error\n");
            }
        });
    }

    private void handleRegister(String message, ChannelHandlerContext ctx) {
        // FORMAT: REGISTER <username> <password> [displayName]
        String[] parts = message.split("\\s+", 4);
        if (parts.length < 3) {
            ctx.writeAndFlush("AUTH_FAIL Usage: REGISTER <username> <password> [displayName]\n");
            return;
        }
        String username    = parts[1];
        String password    = parts[2];
        String displayName = parts.length == 4 ? parts[3] : username;

        authExecutor.submit(() -> {
            try {
                String token = authService.register(username, password, displayName);
                ctx.writeAndFlush("AUTH_OK " + token + "\n");
            } catch (DuplicateUserException e) {
                ctx.writeAndFlush("AUTH_FAIL Username already taken\n");
            } catch (AuthException e) {
                ctx.writeAndFlush("AUTH_FAIL " + e.getMessage() + "\n");
            } catch (Exception e) {
                log.error("Register handler error: {}", e.getMessage(), e);
                ctx.writeAndFlush("AUTH_FAIL Internal server error\n");
            }
        });
    }
}