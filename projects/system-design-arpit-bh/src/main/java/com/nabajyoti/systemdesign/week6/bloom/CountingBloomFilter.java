package com.nabajyoti.systemdesign.week6.bloom;

import java.util.HashMap;
import java.util.Map;

public final class CountingBloomFilter {
    private final byte[] slots;
    private final int k;
    private final Map<String, Integer> live = new HashMap<>();

    public CountingBloomFilter(int m, int k) {
        if (m <= 0 || k <= 0) {
            throw new IllegalArgumentException("m and k must be positive");
        }
        this.slots = new byte[m];
        this.k = k;
    }

    public void add(String key) {
        int[] indexes = DoubleHash.indexes(key, slots.length, k);
        for (int index : indexes) {
            if (slots[index] < 15) {
                slots[index]++;
            }
        }
        live.merge(key, 1, Integer::sum);
    }

    public boolean mightContain(String key) {
        int[] indexes = DoubleHash.indexes(key, slots.length, k);
        for (int index : indexes) {
            if (slots[index] == 0) {
                return false;
            }
        }
        return true;
    }

    public boolean remove(String key) {
        Integer held = live.get(key);
        if (held == null) {
            return false;
        }
        if (held == 1) {
            live.remove(key);
        } else {
            live.put(key, held - 1);
        }
        int[] indexes = DoubleHash.indexes(key, slots.length, k);
        for (int index : indexes) {
            if (slots[index] > 0 && slots[index] < 15) {
                slots[index]--;
            }
        }
        return true;
    }

    /** Test probe. Not part of the filter API. */
    int counter(int slot) {
        return slots[slot] & 0xff;
    }

    /** Test probe. Not part of the filter API. */
    int nonZeroSlots() {
        int count = 0;
        for (byte slot : slots) {
            if (slot != 0) {
                count++;
            }
        }
        return count;
    }

    /** Test probe. Not part of the filter API. */
    int slotSum() {
        int sum = 0;
        for (byte slot : slots) {
            sum += slot & 0xff;
        }
        return sum;
    }

    /** Test probe. Not part of the filter API. */
    int[] snapshot() {
        int[] copy = new int[slots.length];
        for (int i = 0; i < slots.length; i++) {
            copy[i] = slots[i] & 0xff;
        }
        return copy;
    }
}
