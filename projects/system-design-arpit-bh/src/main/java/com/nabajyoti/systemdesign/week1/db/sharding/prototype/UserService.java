package com.nabajyoti.systemdesign.week1.db.sharding.prototype;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    public List<User> getAllUsers() {

        List<CompletableFuture<List<User>>> futures =
                new ArrayList<>();

        for (Shard shard : Shard.values()) {

            CompletableFuture<List<User>> future =
                    CompletableFuture.supplyAsync(() -> {

                        try {

                            System.out.println(
                                    "Querying " + shard +
                                            " on thread " +
                                            Thread.currentThread().getName()
                            );

                            return userRepository.findAll(shard);

                        } catch (SQLException e) {

                            throw new RuntimeException(e);
                        }
                    });

            futures.add(future);
        }

        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();
    }
}