package com.nabajyoti.systemdesign.week6.bloom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void addThenRemoveRestoresSlots() {
        CountingBloomFilter filter = new CountingBloomFilter(32, 3);
        int[] empty = filter.snapshot();
        filter.add("alpha");
        assertTrue(filter.remove("alpha"));
        assertArrayEquals(empty, filter.snapshot());
        assertFalse(filter.mightContain("alpha"));
    }

    @Test
    void removeOfUnknownKeyLeavesSlotsUntouched() {
        CountingBloomFilter filter = new CountingBloomFilter(32, 3);
        filter.add("present");
        int[] before = filter.snapshot();
        assertFalse(filter.remove("absent"));
        assertArrayEquals(before, filter.snapshot());
        assertTrue(filter.mightContain("present"));
    }

    @Test
    void removeOfFalsePositiveLeavesSlotsUntouched() {
        CountingBloomFilter filter = new CountingBloomFilter(8, 3);
        filter.add("present");
        String probe = null;
        for (int i = 0; i < 10_000; i++) {
            String candidate = "probe-" + i;
            if (filter.mightContain(candidate)) {
                probe = candidate;
                break;
            }
        }
        assertNotNull(probe, "expected a false positive in 10000 probes");
        int[] before = filter.snapshot();
        assertFalse(filter.remove(probe));
        assertArrayEquals(before, filter.snapshot());
        assertTrue(filter.mightContain("present"));
    }

    @Test
    void addTwiceRemoveOnceStillPresent() {
        CountingBloomFilter filter = new CountingBloomFilter(32, 3);
        filter.add("alpha");
        filter.add("alpha");
        assertTrue(filter.remove("alpha"));
        assertTrue(filter.mightContain("alpha"));
        assertEquals(3, filter.slotSum());
    }

    @Test
    void slotSumEqualsKTimesLiveKeys() {
        CountingBloomFilter filter = new CountingBloomFilter(64, 4);
        filter.add("a");
        filter.add("b");
        filter.add("c");
        assertEquals(4 * 3, filter.slotSum());
        filter.remove("b");
        assertEquals(4 * 2, filter.slotSum());
    }

    @Test
    void saturatedCounterIsNotDecremented() {
        CountingBloomFilter filter = new CountingBloomFilter(1, 1);
        for (int n = 0; n < 20; n++) {
            filter.add("x");
        }
        assertEquals(15, filter.counter(0));
        assertTrue(filter.remove("x"));
        assertEquals(15, filter.counter(0));
        assertTrue(filter.mightContain("x"));
    }
}