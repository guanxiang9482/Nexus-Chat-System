Create database if not exists nexus_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE nexus_db;

CREATE TABLE IF NOT EXISTS users (
    id            CHAR(36)        NOT NULL,
    username      VARCHAR(32)     NOT NULL,
    password_hash VARCHAR(72)     NOT NULL,
    display_name  VARCHAR(64)     NOT NULL,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP       NULL,
    is_active     TINYINT(1)      NOT NULL DEFAULT 1,

    PRIMARY KEY (id),
    UNIQUE INDEX uq_username (username),
    INDEX        idx_active   (is_active, username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- ROOMS
-- A room is a conversation space. Can be public or private.
-- created_by references the user who owns/created the room.
-- ============================================================
CREATE TABLE IF NOT EXISTS rooms (
    id          CHAR(36)        NOT NULL,
    name        VARCHAR(64)     NOT NULL,
    type        ENUM('public','private') NOT NULL DEFAULT 'public',
    created_by  CHAR(36)        NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active   TINYINT(1)      NOT NULL DEFAULT 1,

    PRIMARY KEY  (id),
    UNIQUE INDEX uq_room_name   (name),
    INDEX        idx_created_by (created_by),
    INDEX        idx_active     (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ============================================================
-- ROOM_MEMBERS
-- Junction table. One row = one user in one room.
-- role: owner can delete the room, moderator can kick,
--       member is a regular participant.
-- No FOREIGN KEY constraints intentionally — we manage
-- referential integrity in the application layer for
-- flexibility and performance at scale.
-- ============================================================
CREATE TABLE IF NOT EXISTS room_members (
    room_id     CHAR(36)        NOT NULL,
    user_id     CHAR(36)        NOT NULL,
    role        ENUM('owner','moderator','member') NOT NULL DEFAULT 'member',
    joined_at   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY  (room_id, user_id),
    INDEX        idx_user_rooms (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ============================================================
-- MESSAGES
-- Every message ever sent.
-- type: 'text'   = normal user message
--       'system' = server notification ("Alice joined")
--       'blocked'= AI blocked this message (Milestone 4)
-- sender_id is NULL for system messages.
-- ============================================================
CREATE TABLE IF NOT EXISTS messages (
    id          CHAR(36)        NOT NULL,
    room_id     CHAR(36)        NOT NULL,
    sender_id   CHAR(36)        NULL,
    content     TEXT            NOT NULL,
    type        ENUM('text','system','blocked') NOT NULL DEFAULT 'text',
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY  (id),
    INDEX        idx_room_messages (room_id, created_at),
    INDEX        idx_sender        (sender_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;