package com.nabajyoti.systemdesign.week1.db.sharding.prototype;

public class ShardRouter {

    public Shard getShard(long userId) {

        int shardId = (int) (userId % 2);

        if (shardId == 0) {
            return Shard.SHARD_0;
        }

        return Shard.SHARD_1;
    }
}
