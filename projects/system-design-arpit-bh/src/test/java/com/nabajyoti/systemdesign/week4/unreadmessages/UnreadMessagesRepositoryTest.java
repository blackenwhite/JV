package com.nabajyoti.systemdesign.week4.unreadmessages;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnreadMessagesRepositoryTest {

    private static final String URL = "jdbc:postgresql://localhost:5435/pgdb";
    private static final String USER = "admin";
    private static final String PASSWORD = "password";

    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection(URL, USER, PASSWORD);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    DROP TABLE IF EXISTS messages
                    """);
            statement.execute("""
                    CREATE TABLE messages (
                        id BIGSERIAL PRIMARY KEY,
                        conversation_id BIGINT NOT NULL,
                        sender_id BIGINT NOT NULL,
                        recipient_id BIGINT NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                        read_at TIMESTAMPTZ
                    )
                    """);
            statement.execute("""
                    INSERT INTO messages
                        (conversation_id, sender_id, recipient_id, read_at)
                    VALUES
                        (1, 20, 10, NULL),
                        (1, 20, 10, NULL),
                        (1, 20, 10, now()),
                        (1, 30, 99, NULL),
                        (2, 20, 10, NULL)
                    """);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void countsOnlyUnreadMessagesForRecipientAndConversation() throws Exception {
        UnreadMessageRepository repository = new UnreadMessageRepository(connection);

        int unread = repository.countUnread(10, 1);

        assertEquals(2, unread);
    }
}