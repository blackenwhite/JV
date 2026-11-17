package com.nabajyoti.systemdesign.week4.rediscluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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


    @Test
    void locateReturnsTheMasterThatOwnsTheSlot() {
        Set<HostAndPort> nodes = Set.of(
                new HostAndPort("192.168.1.3", 7100),
                new HostAndPort("192.168.1.3", 7101),
                new HostAndPort("192.168.1.3", 7102)
        );

        try (ClusterStore store = new ClusterStore(nodes)) {
            KeyLocation location = store.locate("city");

            assertTrue(location.slot() >= 0 && location.slot() <= 16383);
            assertEquals("192.168.1.3", location.masterHost());
            assertEquals(masterPortFor(location.slot()), location.masterPort());
            assertEquals(location, store.locate("city"));
        }
    }

    private static int masterPortFor(long slot) {
        if (slot <= 5460) {
            return 7100;
        }
        if (slot <= 10922) {
            return 7101;
        }
        return 7102;
    }
}