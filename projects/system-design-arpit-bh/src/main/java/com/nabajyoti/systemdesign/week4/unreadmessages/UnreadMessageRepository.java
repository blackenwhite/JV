package com.nabajyoti.systemdesign.week4.unreadmessages;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UnreadMessageRepository {

    private final Connection connection;

    public UnreadMessageRepository(Connection connection) {
        this.connection = connection;
    }

    public int countUnread(long recipientId, long conversationId) throws SQLException {
        String sql = """
            SELECT COUNT(*)
            FROM messages
            WHERE recipient_id = ?
              AND conversation_id = ?
              AND read_at IS NULL
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, recipientId);
            statement.setLong(2, conversationId);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }
}