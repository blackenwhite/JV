package com.nabajyoti.systemdesign.week6.bloom;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class FprExperiment {
    static final long SEED = 42L;
    static final int N = 1_000;
    static final int PROBES = 80_000;

    private FprExperiment() {}

    public static double measure(int m, int k, int n, int probes) {
        CountingBloomFilter filter = new CountingBloomFilter(m, k);
        for (int i = 0; i < n; i++) {
            filter.add(insertedKey(i));
        }
        int hits = 0;
        for (int i = 0; i < probes; i++) {
            if (filter.mightContain(probeKey(i))) {
                hits++;
            }
        }
        return hits / (double) probes;
    }

    private static String insertedKey(int i) {
        return "ins-" + Long.toHexString(i * 0x9E3779B97F4A7C15L);
    }

    private static String probeKey(int i) {
        return "prb-" + Long.toHexString(i * 0xBF58476D1CE4E5B9L);
    }

    public static void writeFprVsM(Path csv) {
        int k = 4;
        int[] ms = {4_000, 6_000, 8_000, 10_000, 12_000};
        StringBuilder out = new StringBuilder();
        header(out);
        for (int m : ms) {
            row(out, m, k, N, PROBES);
        }
        write(csv, out);
    }

    public static void writeFprVsK(Path csv) {
        int m = 8_000;
        StringBuilder out = new StringBuilder();
        header(out);
        for (int k = 1; k <= 7; k++) {
            row(out, m, k, N, PROBES);
        }
        write(csv, out);
    }

    public static void main(String[] args) {
        Path dir = Path.of("week6-bloom/data");
        writeFprVsM(dir.resolve("fpr-vs-m.csv"));
        writeFprVsK(dir.resolve("fpr-vs-k.csv"));
    }

    private static void header(StringBuilder out) {
        out.append("m,k,n,probes,seed,measured_fpr,theory_fpr");
    }

    private static void row(StringBuilder out, int m, int k, int n, int probes) {
        double measured = measure(m, k, n, probes);
        double theory = BloomTheory.falsePositiveRate(m, k, n);
        out.append('\n');
        out.append(m).append(',');
        out.append(k).append(',');
        out.append(n).append(',');
        out.append(probes).append(',');
        out.append(SEED).append(',');
        out.append(String.format(Locale.US, "%.10f", measured)).append(',');
        out.append(String.format(Locale.US, "%.10f", theory));
    }

    private static void write(Path csv, StringBuilder out) {
        try {
            Path parent = csv.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(csv, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}