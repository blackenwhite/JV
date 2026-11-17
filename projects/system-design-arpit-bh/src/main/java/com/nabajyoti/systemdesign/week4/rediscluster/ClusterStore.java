package com.nabajyoti.systemdesign.week4.rediscluster;

import java.util.Set;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

public class ClusterStore implements AutoCloseable {

    private final JedisCluster cluster;

    public ClusterStore(Set<HostAndPort> nodes) {
        this.cluster = new JedisCluster(nodes);
    }

    public void put(String key, String value) {
        cluster.set(key, value);
    }

    public String get(String key) {
        return cluster.get(key);
    }

    @Override
    public void close() {
        cluster.close();
    }
}