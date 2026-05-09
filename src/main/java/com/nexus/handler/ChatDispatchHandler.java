package com.nexus.handler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nexus.domain.ChatRoom;
import com.nexus.domain.User;
import com.nexus.service.ChatRoomService;
import com.nexus.service.MessageService;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * Routes authenticated commands to the appropriate service.
 *
 * By the time a message reaches this handler:
 *   - JWT has already been verified by AuthMiddlewareHandler
 *   - User object is already attached to the channel
 *   - The TOKEN prefix has been stripped — only the raw command remains
 *
 * This handler is deliberately thin — parse, validate, delegate.
 * No business logic lives here.
 *
 * Supported commands:
 *   /create <roomName> [public|private]
 *   /join   <roomName>
 *   /leave  <roomName>
 *   /msg    <roomName> <message content>
 *   /list
 *
 * @ChannelHandler.Sharable — safe because all state is on the channel.
 */
@ChannelHandler.Sharable
public class ChatDispatchHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(ChatDispatchHandler.class);

    private final ChatRoomService chatRoomService;
    private final MessageService  messageService;

    public ChatDispatchHandler(ChatRoomService chatRoomService,
                               MessageService messageService) {
        this.chatRoomService = chatRoomService;
        this.messageService  = messageService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        String trimmed = msg.trim();
        User   user    = ctx.channel().attr(ChannelAttributes.USER).get();

        // Safety check — should never happen if pipeline is correct
        if (user == null) {
            ctx.channel().writeAndFlush("[SERVER] Authentication error.\n");
            return;
        }

        // ── Route by command prefix ───────────────────────────────────
        if (trimmed.startsWith("/create")) {
            handleCreate(ctx, user, trimmed);
        } else if (trimmed.startsWith("/join")) {
            handleJoin(ctx, user, trimmed);
        } else if (trimmed.startsWith("/leave")) {
            handleLeave(ctx, user, trimmed);
        } else if (trimmed.startsWith("/msg")) {
            handleMsg(ctx, user, trimmed);
        } else if (trimmed.equals("/list")) {
            handleList(ctx);
        } else {
            ctx.channel().writeAndFlush(
                "[SERVER] Unknown command. Try /list /create /join /leave /msg\n"
            );
        }
    }

    // ----------------------------------------------------------------
    // handleCreate()
    // /create <roomName> [public|private]
    // Default type is public if not specified.
    // ----------------------------------------------------------------
    private void handleCreate(ChannelHandlerContext ctx, User user, String msg) {
        // Parse: "/create general" or "/create secret private"
        String[] parts = msg.split("\\s+");

        if (parts.length < 2) {
            ctx.channel().writeAndFlush(
                "[SERVER] Usage: /create <roomName> [public|private]\n"
            );
            return;
        }

        String       roomName = parts[1];
        ChatRoom.Type type    = ChatRoom.Type.PUBLIC; // default

        if (parts.length >= 3) {
            if (parts[2].equalsIgnoreCase("private")) {
                type = ChatRoom.Type.PRIVATE;
            } else if (!parts[2].equalsIgnoreCase("public")) {
                ctx.channel().writeAndFlush(
                    "[SERVER] Room type must be 'public' or 'private'.\n"
                );
                return;
            }
        }

        try {
            ChatRoom room = chatRoomService.createRoom(roomName, type, user);
            ctx.channel().writeAndFlush(
                "[SERVER] Room '" + room.getName() + "' created.\n"
            );
        } catch (Exception e) {
            ctx.channel().writeAndFlush("[SERVER] " + e.getMessage() + "\n");
        }
    }

    // ----------------------------------------------------------------
    // handleJoin()
    // /join <roomName>
    // ----------------------------------------------------------------
    private void handleJoin(ChannelHandlerContext ctx, User user, String msg) {
        String[] parts = msg.split("\\s+");

        if (parts.length < 2) {
            ctx.channel().writeAndFlush("[SERVER] Usage: /join <roomName>\n");
            return;
        }

        String roomName = parts[1];

        try {
            chatRoomService.joinRoom(roomName, user, ctx.channel());
            ctx.channel().writeAndFlush(
                "[SERVER] Joined room '" + roomName + "'.\n"
            );
        } catch (Exception e) {
            ctx.channel().writeAndFlush("[SERVER] " + e.getMessage() + "\n");
        }
    }

    // ----------------------------------------------------------------
    // handleLeave()
    // /leave <roomName>
    // ----------------------------------------------------------------
    private void handleLeave(ChannelHandlerContext ctx, User user, String msg) {
        String[] parts = msg.split("\\s+");

        if (parts.length < 2) {
            ctx.channel().writeAndFlush("[SERVER] Usage: /leave <roomName>\n");
            return;
        }

        String roomName = parts[1];

        try {
            chatRoomService.leaveRoom(roomName, user, ctx.channel());
            ctx.channel().writeAndFlush(
                "[SERVER] Left room '" + roomName + "'.\n"
            );
        } catch (Exception e) {
            ctx.channel().writeAndFlush("[SERVER] " + e.getMessage() + "\n");
        }
    }

    // ----------------------------------------------------------------
    // handleMsg()
    // /msg <roomName> <message content>
    // ----------------------------------------------------------------
    private void handleMsg(ChannelHandlerContext ctx, User user, String msg) {
        // Format: "/msg general hello everyone"
        // Split into max 3 parts — content may contain spaces
        String[] parts = msg.split("\\s+", 3);

        if (parts.length < 3) {
            ctx.channel().writeAndFlush(
                "[SERVER] Usage: /msg <roomName> <message>\n"
            );
            return;
        }

        String roomName = parts[1];
        String content  = parts[2];

        // Find room to get its ID
        try {
            chatRoomService.listRooms().stream()
                .filter(r -> r.getName().equals(roomName))
                .findFirst()
                .ifPresentOrElse(
                    room -> messageService.send(room.getId(), user, content),
                    () -> ctx.channel().writeAndFlush(
                        "[SERVER] Room not found: " + roomName + "\n"
                    )
                );
        } catch (Exception e) {
            ctx.channel().writeAndFlush("[SERVER] " + e.getMessage() + "\n");
        }
    }

    // ----------------------------------------------------------------
    // handleList()
    // /list — shows all active rooms
    // ----------------------------------------------------------------
    private void handleList(ChannelHandlerContext ctx) {
        List<ChatRoom> rooms = chatRoomService.listRooms();

        if (rooms.isEmpty()) {
            ctx.channel().writeAndFlush(
                "[SERVER] No rooms yet. Create one with /create <roomName>\n"
            );
            return;
        }

        StringBuilder sb = new StringBuilder("[SERVER] Active rooms:\n");
        for (ChatRoom room : rooms) {
            sb.append("  - ")
              .append(room.getName())
              .append(" [")
              .append(room.getType().name().toLowerCase())
              .append("]\n");
        }

        ctx.channel().writeAndFlush(sb.toString());
    }

    // ----------------------------------------------------------------
    // channelInactive()
    // Called when a client disconnects — clean up all room memberships
    // ----------------------------------------------------------------
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        User user = ctx.channel().attr(ChannelAttributes.USER).get();

        if (user != null) {
            chatRoomService.handleDisconnect(user, ctx.channel());
            log.info("Cleaned up rooms for disconnected user '{}'",
                     user.getUsername());
        }

        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("ChatDispatchHandler error", cause);
        ctx.close();
    }
}