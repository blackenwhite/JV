package com.nabajyoti.systemdesign.week4.rediscluster;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

import redis.clients.jedis.HostAndPort;

class ClusterStoreTest {

    @Test
    void putAndGetThroughTheCluster() {
        Set<HostAndPort> nodes = Set.of(
                new HostAndPort("192.168.1.3", 7100),
                new HostAndPort("192.168.1.3", 7101),
                new HostAndPort("192.168.1.3", 7102)
        );

        try (ClusterStore store = new ClusterStore(nodes)) {
            store.put("city", "Pune");
            assertEquals("Pune", store.get("city"));
        }
    }
}