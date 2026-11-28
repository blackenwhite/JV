package com.nabajyoti.systemdesign.week6.bloom;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoubleHashTest {

    @Test
    void rejectsNonPositiveMOrK() {
        assertThrows(IllegalArgumentException.class, () -> DoubleHash.indexes("a", 0, 3));
        assertThrows(IllegalArgumentException.class, () -> DoubleHash.indexes("a", 8, 0));
    }

    @Test
    void returnsKIndexesAllInRange() {
        int m = 10;
        int k = 4;
        int[] indexes = DoubleHash.indexes("user:42", m, k);

        assertEquals(k, indexes.length);
        for (int index : indexes) {
            assertTrue(index >= 0 && index < m, () -> "out of range: " + index);
        }
    }

    @Test
    void sameKeySameIndexes() {
        int[] first = DoubleHash.indexes("alpha", 17, 5);
        int[] second = DoubleHash.indexes("alpha", 17, 5);
        assertArrayEquals(first, second);
    }

    @Test
    void secondHashDoesNotCollapseSlots() {
        int m = 11;
        int k = 5;
        int[] indexes = DoubleHash.indexes("hello", m, k);
        assertNotEquals(indexes[0], indexes[1]);
    }

    @Test
    void emptyAndNonAsciiStayInRange() {
        for (String key : Arrays.asList("", "café", "日本語")) {
            int[] indexes = DoubleHash.indexes(key, 7, 3);
            assertEquals(3, indexes.length);
            for (int index : indexes) {
                assertTrue(index >= 0 && index < 7, key + " -> " + index);
            }
        }
    }

    @Test
    void manyKeysStayInRangeAndSpreadWhenMAndKAllowIt() {
        int m = 13;
        int k = 4;
        for (int n = 0; n < 200; n++) {
            int[] indexes = DoubleHash.indexes("k-" + n, m, k);
            Set<Integer> unique = new HashSet<>();
            for (int index : indexes) {
                assertTrue(index >= 0 && index < m);
                unique.add(index);
            }
            assertTrue(unique.size() >= 2);
        }
    }
}