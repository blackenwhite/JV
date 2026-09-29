package com.nabajyoti.systemdesign.week1.db.sharding.prototype;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class UserRepository {

    private final DatabaseConnectionManager connectionManager;

    public UserRepository(
            DatabaseConnectionManager connectionManager) {

        this.connectionManager = connectionManager;
    }

    public void save(User user, Shard shard)
            throws SQLException {

        String sql = """
                INSERT INTO users (id, name, email)
                VALUES (?, ?, ?)
                """;

        try (Connection connection =
                     connectionManager.getConnection(shard);

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, user.id());
            statement.setString(2, user.name());
            statement.setString(3, user.email());

            statement.executeUpdate();
        }
    }
}
