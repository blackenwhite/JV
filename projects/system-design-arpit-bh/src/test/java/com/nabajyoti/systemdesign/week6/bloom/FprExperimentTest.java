package com.nabajyoti.systemdesign.week6.bloom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FprExperimentTest {

    @Test
    void theoryFprForKnOverMOfPointFiveAndK4() {
        assertEquals(0.023968650821013612, BloomTheory.falsePositiveRate(8_000, 4, 1_000), 1e-12);
    }

    @Test
    void measuredFprIsWithinTenPercentOfTheory() {
        int m = 4_000;
        int k = 4;
        int n = 1_000;
        int probes = 20_000;
        double measured = FprExperiment.measure(m, k, n, probes);
        double theory = BloomTheory.falsePositiveRate(m, k, n);
        double relative = Math.abs(measured - theory) / theory;
        assertTrue(relative < 0.10, () -> "measured " + measured + " theory " + theory);
    }

    @Test
    void fprVsMCsvIsByteIdenticalOnRewrite(@TempDir Path dir) throws IOException {
        Path csv = dir.resolve("fpr-vs-m.csv");
        FprExperiment.writeFprVsM(csv);
        byte[] first = Files.readAllBytes(csv);
        FprExperiment.writeFprVsM(csv);
        byte[] second = Files.readAllBytes(csv);
        assertArrayEquals(first, second);
        String text = Files.readString(csv);
        assertTrue(text.startsWith("m,k,n,probes,seed,measured_fpr,theory_fpr\n"));
        assertEquals(6, text.split("\n").length);
    }

    @Test
    void fprVsKCsvIsByteIdenticalOnRewrite(@TempDir Path dir) throws IOException {
        Path csv = dir.resolve("fpr-vs-k.csv");
        FprExperiment.writeFprVsK(csv);
        byte[] first = Files.readAllBytes(csv);
        FprExperiment.writeFprVsK(csv);
        byte[] second = Files.readAllBytes(csv);
        assertArrayEquals(first, second);
        String text = Files.readString(csv);
        assertTrue(text.startsWith("m,k,n,probes,seed,measured_fpr,theory_fpr\n"));
        assertEquals(8, text.split("\n").length);
    }
}