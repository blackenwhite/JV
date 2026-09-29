package com.nabajyoti.systemdesign.week1.db.sharding.prototype;

import java.sql.SQLException;

public class UserService {

    private final ShardRouter shardRouter;
    private final UserRepository userRepository;

    public UserService(
            ShardRouter shardRouter,
            UserRepository userRepository) {

        this.shardRouter = shardRouter;
        this.userRepository = userRepository;
    }

    public void createUser(User user)
            throws SQLException {

        Shard shard =
                shardRouter.getShard(user.id());

        System.out.println(
                "Routing user " +
                        user.id() +
                        " to " +
                        shard
        );

        userRepository.save(user, shard);
    }
}