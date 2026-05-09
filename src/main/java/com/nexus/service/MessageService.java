package com.nexus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;
import com.nexus.domain.Message;
import com.nexus.domain.User;
import com.nexus.event.MessageCreatedEvent;
import com.nexus.repository.MessageRepository;

/**
 * Handles sending a message.
 *
 * Single responsibility:
 *   1. Validate the message
 *   2. Persist it via MessageRepository
 *   3. Publish MessageCreatedEvent to the EventBus
 *
 * Does NOT know about broadcasting. Does NOT call ChatRoomService.
 * Broadcasting is the job of NotificationSubscriber, which listens
 * for MessageCreatedEvent on the EventBus.
 */
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    // Maximum message length — enforced at service layer
    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final MessageRepository messageRepository;
    private final EventBus          eventBus;

    public MessageService(MessageRepository messageRepository, EventBus eventBus) {
        this.messageRepository = messageRepository;
        this.eventBus          = eventBus;
    }

    // ----------------------------------------------------------------
    // send()
    // Main entry point. Called by ChatDispatchHandler when user
    // types /msg <content>.
    // ----------------------------------------------------------------
    public void send(String roomId, User sender, String content) {
        // Validate content
        if (content == null || content.isBlank()) {
            log.warn("Empty message rejected from user '{}'", sender.getUsername());
            return;
        }

        if (content.length() > MAX_MESSAGE_LENGTH) {
            log.warn("Message too long from user '{}': {} chars",
                     sender.getUsername(), content.length());
            return;
        }

        // Build domain object
        Message message = Message.newTextMessage(roomId, sender.getId(), content);

        // Persist FIRST — if this fails, we don't broadcast a message
        // that was never saved. Consistency over speed.
        messageRepository.save(message);

        // Publish event — NotificationSubscriber handles broadcast
        eventBus.post(new MessageCreatedEvent(message, sender));

        log.debug("Message from '{}' saved and event published to room '{}'",
                  sender.getUsername(), roomId);
    }

    // ----------------------------------------------------------------
    // sendSystemMessage()
    // Used internally for server notifications like "Alice joined".
    // These are persisted too — room history includes system messages.
    // ----------------------------------------------------------------
    public void sendSystemMessage(String roomId, String content) {
        Message message = Message.newSystemMessage(roomId, content);
        messageRepository.save(message);

        log.debug("System message saved to room '{}'", roomId);
    }
}