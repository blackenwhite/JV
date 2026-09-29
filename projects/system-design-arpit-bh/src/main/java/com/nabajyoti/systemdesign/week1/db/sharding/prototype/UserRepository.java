package com.nabajyoti.systemdesign.week1.db.sharding.prototype;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    public List<User> findAll(Shard shard)
            throws SQLException {

        String sql = """
            SELECT id, name, email
            FROM users
            ORDER BY id
            """;

        List<User> users = new ArrayList<>();

        try (Connection connection =
                     connectionManager.getConnection(shard);

             PreparedStatement statement =
                     connection.prepareStatement(sql);

             ResultSet resultSet =
                     statement.executeQuery()) {

            while (resultSet.next()) {

                users.add(
                        new User(
                                resultSet.getLong("id"),
                                resultSet.getString("name"),
                                resultSet.getString("email")
                        )
                );
            }
        }

        return users;
    }

    public Optional<User> findById(
            long userId,
            Shard shard) throws SQLException {

        String sql = """
                SELECT id, name, email
                FROM users
                WHERE id = ?
                """;

        try (Connection connection =
                     connectionManager.getConnection(shard);

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, userId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {

                    User user = new User(
                            resultSet.getLong("id"),
                            resultSet.getString("name"),
                            resultSet.getString("email")
                    );

                    return Optional.of(user);
                }

                return Optional.empty();
            }
        }
    }

}
