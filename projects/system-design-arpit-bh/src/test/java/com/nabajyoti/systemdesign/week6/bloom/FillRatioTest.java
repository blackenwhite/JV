package com.nabajyoti.systemdesign.week6.bloom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FillRatioTest {

    @Test
    void theoryFillRatioForKnOverMOfPointSeven() {
        assertEquals(0.5034146962085905, BloomTheory.fillRatio(10_000, 7, 1_000), 1e-12);
    }

    @Test
    void emptyFilterHasFillRatioZero() {
        CountingBloomFilter filter = new CountingBloomFilter(10_000, 7);
        assertEquals(0, filter.nonZeroSlots());
    }

    @Test
    void fillRatioAfterNInsertsTracksTheory() {
        int m = 10_000;
        int k = 7;
        int n = 1_000;
        CountingBloomFilter filter = new CountingBloomFilter(m, k);
        for (int i = 0; i < n; i++) {
            filter.add("key-" + i);
        }

        double measured = filter.nonZeroSlots() / (double) m;
        double theory = 0.5034146962085905;
        assertTrue(
                Math.abs(measured - theory) < 0.03,
                () -> "measured fill " + measured + " vs theory " + theory
        );
    }
}