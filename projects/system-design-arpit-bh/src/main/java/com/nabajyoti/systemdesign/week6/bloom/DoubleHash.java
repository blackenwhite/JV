package com.nabajyoti.systemdesign.week6.bloom;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class DoubleHash {
    private static final long P1 = 31L;
    private static final long MOD1 = 1_000_000_007L;
    private static final long P2 = 53L;
    private static final long MOD2 = 1_000_000_009L;

    private DoubleHash() {}

    public static int[] indexes(String key, int m, int k) {
        Objects.requireNonNull(key, "key");
        if (m <= 0 || k <= 0) {
            throw new IllegalArgumentException("m and k must be positive");
        }

        long h1 = 0;
        long h2 = 0;
        for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            h1 = (h1 * P1 + c) % MOD1;
            h2 = (h2 * P2 + c) % MOD2;
        }

        int base = (int) (h1 % m);
        int step = (int) (h2 % m);
        if (step == 0) {
            step = 1;
        }

        int[] indexes = new int[k];
        for (int i = 0; i < k; i++) {
            indexes[i] = (int) ((base + (long) i * step) % m);
        }
        return indexes;
    }
}