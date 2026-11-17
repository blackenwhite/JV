package com.nabajyoti.systemdesign.week4.rediscluster;

import java.util.List;
import java.util.Set;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.resps.ClusterShardInfo;
import redis.clients.jedis.resps.ClusterShardNodeInfo;

public class ClusterStore implements AutoCloseable {

    private final Set<HostAndPort> nodes;
    private final JedisCluster cluster;

    public ClusterStore(Set<HostAndPort> nodes) {
        this.nodes = nodes;
        this.cluster = new JedisCluster(nodes);
    }

    public void put(String key, String value) {
        cluster.set(key, value);
    }

    public String get(String key) {
        return cluster.get(key);
    }

    public KeyLocation locate(String key) {
        HostAndPort anyNode = nodes.iterator().next();
        try (Jedis jedis = new Jedis(anyNode.getHost(), anyNode.getPort())) {
            long slot = jedis.clusterKeySlot(key);
            for (ClusterShardInfo shard : jedis.clusterShards()) {
                for (List<Long> range : shard.getSlots()) {
                    long start = range.get(0);
                    long end = range.get(1);
                    if (slot < start || slot > end) {
                        continue;
                    }
                    for (ClusterShardNodeInfo node : shard.getNodes()) {
                        if ("master".equals(node.getRole())) {
                            return new KeyLocation(slot, node.getIp(), node.getPort().intValue());
                        }
                    }
                }
            }
            throw new IllegalStateException("no master owns slot " + slot);
        }
    }

    @Override
    public void close() {
        cluster.close();
    }
}