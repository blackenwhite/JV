package com.nabajyoti.systemdesign.week1.db.sharding.prototype;

import java.sql.Connection;

public class Main {

    public static void main(String[] args)
            throws Exception {

        ShardRouter shardRouter =
                new ShardRouter();

        DatabaseConnectionManager connectionManager =
                new DatabaseConnectionManager();

        UserRepository userRepository =
                new UserRepository(connectionManager);

        UserService userService =
                new UserService(
                        shardRouter,
                        userRepository
                );

        User alice = new User(
                101,
                "Alice",
                "alice@example.com"
        );

        userService.createUser(alice);
    }
}
