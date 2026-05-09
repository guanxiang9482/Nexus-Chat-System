package com.nexus.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;
import com.nexus.domain.ChatRoom;
import com.nexus.domain.Message;
import com.nexus.domain.User;
import com.nexus.event.UserJoinedRoomEvent;
import com.nexus.event.UserLeftRoomEvent;
import com.nexus.exception.RepositoryException;
import com.nexus.repository.MessageRepository;
import com.nexus.repository.RoomRepository;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * Manages room lifecycle — create, join, leave, list.
 *
 * Owns a ConcurrentHashMap of ChannelGroups, one per room.
 * A ChannelGroup is Netty's broadcast list — writing to it
 * delivers the message to every connected user in that room.
 *
 * Publishes UserJoinedRoomEvent and UserLeftRoomEvent to the
 * EventBus after state changes. Never calls NotificationSubscriber
 * directly — that would couple services together.
 */
public class ChatRoomService {

    private static final Logger log = LoggerFactory.getLogger(ChatRoomService.class);

    private final RoomRepository    roomRepository;
    private final MessageRepository messageRepository;
    private final EventBus          eventBus;

    /**
     * One ChannelGroup per room ID.
     * ConcurrentHashMap — safe for concurrent Netty worker threads.
     * ChannelGroup automatically removes dead channels.
     */
    private final ConcurrentHashMap<String, ChannelGroup> roomChannels
            = new ConcurrentHashMap<>();

    public ChatRoomService(RoomRepository roomRepository,
                           MessageRepository messageRepository,
                           EventBus eventBus) {
        this.roomRepository    = roomRepository;
        this.messageRepository = messageRepository;
        this.eventBus          = eventBus;
    }

    // ----------------------------------------------------------------
    // createRoom()
    // Creates a new room and immediately adds the creator as owner.
    // ----------------------------------------------------------------
    public ChatRoom createRoom(String name, ChatRoom.Type type, User creator) {
        // Check if room name already taken
        if (roomRepository.findByName(name).isPresent()) {
            throw new RepositoryException("Room already exists: " + name, null);
        }

        // Build domain object — UUID generated inside newRoom()
        ChatRoom room = ChatRoom.newRoom(name, type, creator.getId());

        // Persist to database
        roomRepository.save(room);

        // Add creator as owner in room_members
        roomRepository.addMember(room.getId(), creator.getId(), "owner");

        // Create an empty ChannelGroup for this room
        roomChannels.put(room.getId(), new DefaultChannelGroup(GlobalEventExecutor.INSTANCE));

        log.info("Room created: '{}' by '{}'", name, creator.getUsername());
        return room;
    }

    // ----------------------------------------------------------------
    // joinRoom()
    // Adds a user to a room — both in DB and in the ChannelGroup.
    // Sends recent message history to the joining user.
    // Publishes UserJoinedRoomEvent.
    // ----------------------------------------------------------------
    public void joinRoom(String roomName, User user, Channel channel) {
        // Find the room
        ChatRoom room = roomRepository.findByName(roomName)
            .orElseThrow(() -> new RepositoryException("Room not found: " + roomName, null));

        // Check if already a member
        if (roomRepository.isMember(room.getId(), user.getId())) {
            channel.writeAndFlush("[SERVER] You are already in room: " + roomName + "\n");
            return;
        }

        // Persist membership
        roomRepository.addMember(room.getId(), user.getId(), "member");

        // Add channel to the room's ChannelGroup
        // computeIfAbsent handles the case where server restarted
        // and the in-memory map lost the group even though DB has members
        ChannelGroup group = roomChannels.computeIfAbsent(
            room.getId(),
            id -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
        );
        group.add(channel);

        // Send recent message history to the joining user
        sendHistory(room.getId(), channel);

        // Publish event — NotificationSubscriber will broadcast to room
        eventBus.post(new UserJoinedRoomEvent(user, room));

        log.info("User '{}' joined room '{}'", user.getUsername(), roomName);
    }

    // ----------------------------------------------------------------
    // leaveRoom()
    // Removes user from room — both in DB and ChannelGroup.
    // Publishes UserLeftRoomEvent.
    // ----------------------------------------------------------------
    public void leaveRoom(String roomName, User user, Channel channel) {
        ChatRoom room = roomRepository.findByName(roomName)
            .orElseThrow(() -> new RepositoryException("Room not found: " + roomName, null));

        if (!roomRepository.isMember(room.getId(), user.getId())) {
            channel.writeAndFlush("[SERVER] You are not in room: " + roomName + "\n");
            return;
        }

        // Remove from DB
        roomRepository.removeMember(room.getId(), user.getId());

        // Remove channel from ChannelGroup
        ChannelGroup group = roomChannels.get(room.getId());
        if (group != null) {
            group.remove(channel);
        }

        // Publish event
        eventBus.post(new UserLeftRoomEvent(user, room));

        log.info("User '{}' left room '{}'", user.getUsername(), roomName);
    }

    // ----------------------------------------------------------------
    // listRooms()
    // Returns all active rooms for the /list command.
    // ----------------------------------------------------------------
    public List<ChatRoom> listRooms() {
        return roomRepository.findAllActive();
    }

    // ----------------------------------------------------------------
    // broadcast()
    // Sends a message string to every channel in a room.
    // Called by NotificationSubscriber.
    // ----------------------------------------------------------------
    public void broadcast(String roomId, String message) {
        ChannelGroup group = roomChannels.get(roomId);
        if (group != null && !group.isEmpty()) {
            group.writeAndFlush(message);
        }
    }

    // ----------------------------------------------------------------
    // handleDisconnect()
    // Called when a channel closes unexpectedly.
    // Removes the user from all rooms they were in.
    // ----------------------------------------------------------------
    public void handleDisconnect(User user, Channel channel) {
        List<String> roomIds = roomRepository.findRoomIdsByUserId(user.getId());

        for (String roomId : roomIds) {
            roomRepository.removeMember(roomId, user.getId());

            ChannelGroup group = roomChannels.get(roomId);
            if (group != null) {
                group.remove(channel);
            }

            // Find room to publish event
            roomRepository.findById(roomId).ifPresent(room ->
                eventBus.post(new UserLeftRoomEvent(user, room))
            );
        }

        log.info("User '{}' disconnected and removed from {} rooms",
                 user.getUsername(), roomIds.size());
    }

    // ----------------------------------------------------------------
    // getChannelGroup()
    // Returns the ChannelGroup for a room.
    // Used by ChatDispatchHandler to associate a channel with a room.
    // ----------------------------------------------------------------
    public Optional<ChannelGroup> getChannelGroup(String roomId) {
        return Optional.ofNullable(roomChannels.get(roomId));
    }

    // ----------------------------------------------------------------
    // sendHistory() — private helper
    // Fetches last 20 messages and sends them to the joining user.
    // ----------------------------------------------------------------
    private void sendHistory(String roomId, Channel channel) {
        List<Message> history = messageRepository.findRecentByRoomId(roomId, 20);

        if (history.isEmpty()) {
            channel.writeAndFlush("[SERVER] No messages yet. Be the first!\n");
            return;
        }

        channel.writeAndFlush("[SERVER] --- Last " + history.size() + " messages ---\n");

        for (Message msg : history) {
            if (msg.isSystemMessage()) {
                channel.writeAndFlush("[SERVER] " + msg.getContent() + "\n");
            } else {
                channel.writeAndFlush(msg.getContent() + "\n");
            }
        }

        channel.writeAndFlush("[SERVER] --- End of history ---\n");
    }
}