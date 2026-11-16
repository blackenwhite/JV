package com.nabajyoti.systemdesign.week4.hashtagcounter;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public final class LatencyHashtagCountDatabase implements HashtagCountDatabase {

    public static final Duration DEFAULT_WRITE_LATENCY = Duration.ofMillis(10);

    private final HashtagCountDatabase delegate;
    private final Duration writeLatency;
    private final Sleeper sleeper;
    private long simulatedSleepNanos;

    public LatencyHashtagCountDatabase(HashtagCountDatabase delegate) {
        this(delegate, DEFAULT_WRITE_LATENCY, Thread::sleep);
    }

    LatencyHashtagCountDatabase(
            HashtagCountDatabase delegate,
            Duration writeLatency,
            Sleeper sleeper
    ) {
        this.delegate = Objects.requireNonNull(delegate);
        this.writeLatency = Objects.requireNonNull(writeLatency);
        this.sleeper = Objects.requireNonNull(sleeper);

        if (writeLatency.isNegative()) {
            throw new IllegalArgumentException("writeLatency must not be negative");
        }
    }

    @Override
    public void increment(String hashtag, long amount) {
        try {
            sleeper.sleep(writeLatency);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during fake database save", exception);
        }

        simulatedSleepNanos += writeLatency.toNanos();
        delegate.increment(hashtag, amount);
    }

    @Override
    public Map<String, Long> snapshot() {
        return delegate.snapshot();
    }

    @Override
    public long writeCount() {
        return delegate.writeCount();
    }

    public Duration simulatedSleep() {
        return Duration.ofNanos(simulatedSleepNanos);
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }
}
