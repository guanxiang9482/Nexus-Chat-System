package com.nexus.subscriber;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;
import com.nexus.event.MessageCreatedEvent;
import com.nexus.event.UserJoinedRoomEvent;
import com.nexus.event.UserLeftRoomEvent;
import com.nexus.service.ChatRoomService;

/**
 * Listens for domain events on the EventBus and broadcasts
 * the appropriate message to every user in the affected room.
 *
 * This is the ONLY class that connects the service layer
 * to the network broadcast layer.
 *
 * Registered once at startup in NexusApplication.
 * Never called directly by any service.
 */
public class NotificationSubscriber {

    private static final Logger log = LoggerFactory.getLogger(NotificationSubscriber.class);

    private final ChatRoomService chatRoomService;

    public NotificationSubscriber(ChatRoomService chatRoomService) {
        this.chatRoomService = chatRoomService;
    }

    // ----------------------------------------------------------------
    // onMessageCreated()
    // Fires when MessageService publishes a MessageCreatedEvent.
    // Formats and broadcasts the message to everyone in the room.
    // ----------------------------------------------------------------
    @Subscribe
    public void onMessageCreated(MessageCreatedEvent event) {
        // Format: "username: message content"
        String broadcast = event.getSender().getUsername()
                         + ": "
                         + event.getMessage().getContent()
                         + "\n";

        chatRoomService.broadcast(event.getMessage().getRoomId(), broadcast);

        log.debug("Broadcasted message from '{}' to room '{}'",
                  event.getSender().getUsername(),
                  event.getMessage().getRoomId());
    }

    // ----------------------------------------------------------------
    // onUserJoined()
    // Fires when ChatRoomService publishes a UserJoinedRoomEvent.
    // Broadcasts a system notification to everyone in the room.
    // ----------------------------------------------------------------
    @Subscribe
    public void onUserJoined(UserJoinedRoomEvent event) {
        String broadcast = "[SERVER] "
                         + event.getUser().getUsername()
                         + " joined the room.\n";

        chatRoomService.broadcast(event.getRoom().getId(), broadcast);

        log.info("User '{}' join notification sent to room '{}'",
                 event.getUser().getUsername(),
                 event.getRoom().getName());
    }

    // ----------------------------------------------------------------
    // onUserLeft()
    // Fires when ChatRoomService publishes a UserLeftRoomEvent.
    // Broadcasts a system notification to everyone in the room.
    // ----------------------------------------------------------------
    @Subscribe
    public void onUserLeft(UserLeftRoomEvent event) {
        String broadcast = "[SERVER] "
                         + event.getUser().getUsername()
                         + " left the room.\n";

        chatRoomService.broadcast(event.getRoom().getId(), broadcast);

        log.info("User '{}' leave notification sent to room '{}'",
                 event.getUser().getUsername(),
                 event.getRoom().getName());
    }
}