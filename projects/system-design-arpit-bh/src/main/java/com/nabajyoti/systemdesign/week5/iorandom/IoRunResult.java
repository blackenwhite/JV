package com.nabajyoti.systemdesign.week5.iorandom;

public record IoRunResult(long bytes, long operations, long elapsedNanos) {

    public double megaBytesPerSecond() {
        if (elapsedNanos <= 0L) {
            return 0.0;
        }
        double megaBytes = bytes / (1024.0 * 1024.0);
        double seconds = elapsedNanos / 1_000_000_000.0;
        return megaBytes / seconds;
    }

    public double operationsPerSecond() {
        if (elapsedNanos <= 0L) {
            return 0.0;
        }
        double seconds = elapsedNanos / 1_000_000_000.0;
        return operations / seconds;
    }
}
