package com.nabajyoti.systemdesign.week6.bloom;

public final class BloomTheory {
    private BloomTheory() {}

    public static double fillRatio(int m, int k, int n) {
        if (m <= 0) {
            throw new IllegalArgumentException("m must be positive");
        }
        return 1.0 - Math.exp(-(double) k * n / m);
    }

    public static double falsePositiveRate(int m, int k, int n) {
        return Math.pow(fillRatio(m, k, n), k);
    }
}