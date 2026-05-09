package com.nexus.handler;

import com.nexus.domain.User;

import io.netty.util.AttributeKey;

/**
 * Central registry for all Netty channel attributes.
 * AttributeKey is typed — no casting needed when reading.
 * Defined once here so both handlers reference the same key.
 */
public final class ChannelAttributes {

    // Stores the verified User object on the channel after login
    public static final AttributeKey<User> USER =
            AttributeKey.valueOf("NEXUS_USER");

    // Stores the room name the user is currently in
    public static final AttributeKey<String> CURRENT_ROOM =
            AttributeKey.valueOf("NEXUS_CURRENT_ROOM");

    // Private constructor — this is a constants class, never instantiated
    private ChannelAttributes() {}
}