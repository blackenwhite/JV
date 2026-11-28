package com.nabajyoti.systemdesign.week6.bloom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountingBloomFilterTest {

    @Test
    void rejectsNonPositiveMOrK() {
        assertThrows(IllegalArgumentException.class, () -> new CountingBloomFilter(0, 3));
        assertThrows(IllegalArgumentException.class, () -> new CountingBloomFilter(8, 0));
    }

    @Test
    void emptyFilterDoesNotContainAKey() {
        CountingBloomFilter filter = new CountingBloomFilter(32, 3);
        assertFalse(filter.mightContain("alpha"));
    }

    @Test
    void addThenMightContainIsTrue() {
        CountingBloomFilter filter = new CountingBloomFilter(32, 3);
        filter.add("alpha");
        assertTrue(filter.mightContain("alpha"));
    }

    @Test
    void addedKeysNeverReturnFalse() {
        CountingBloomFilter filter = new CountingBloomFilter(64, 4);
        String[] keys = {"a", "b", "café", ""};
        for (String key : keys) {
            filter.add(key);
        }
        for (String key : keys) {
            assertTrue(filter.mightContain(key), key);
        }
    }

    @Test
    void queryDoesNotInsert() {
        CountingBloomFilter filter = new CountingBloomFilter(32, 3);
        assertFalse(filter.mightContain("ghost"));
        assertFalse(filter.mightContain("ghost"));
    }

    @Test
    void counterStopsAtFifteen() {
        CountingBloomFilter filter = new CountingBloomFilter(1, 1);
        for (int n = 0; n < 20; n++) {
            filter.add("x");
        }
        assertEquals(15, filter.counter(0));
        assertTrue(filter.mightContain("x"));
    }
}