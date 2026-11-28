package com.nabajyoti.systemdesign.week6.bloom;

public final class CountingBloomFilter {
    private final byte[] slots;
    private final int k;

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

    int counter(int slot) {
        return slots[slot] & 0xff;
    }
}