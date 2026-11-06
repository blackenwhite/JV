package com.nabajyoti.systemdesign.week3.snowflake;

import java.util.Objects;
import java.util.function.LongSupplier;

public final class SnowflakeIdGenerator {
    private final long workerId;
    private final LongSupplier clockMillis;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long workerId) {
        this(workerId, System::currentTimeMillis);
    }

    SnowflakeIdGenerator(long workerId, LongSupplier clockMillis) {
        if (workerId < 0 || workerId > SnowflakeLayout.MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    "workerId must be between 0 and "
                            + SnowflakeLayout.MAX_WORKER_ID
            );
        }

        this.clockMillis = Objects.requireNonNull(clockMillis);
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long currentTimestamp = currentTimestamp();

        if (currentTimestamp < lastTimestamp) {
            throw new IllegalStateException(
                    "System clock moved backwards by "
                            + (lastTimestamp - currentTimestamp)
                            + " milliseconds"
            );
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & SnowflakeLayout.MAX_SEQUENCE;

            if (sequence == 0) {
                currentTimestamp = waitUntilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = currentTimestamp;

        return (currentTimestamp << SnowflakeLayout.TIMESTAMP_SHIFT)
                | (workerId << SnowflakeLayout.WORKER_ID_SHIFT)
                | sequence;
    }

    private long currentTimestamp() {
        long timestamp = clockMillis.getAsLong()
                - SnowflakeLayout.CUSTOM_EPOCH;

        if (timestamp < 0) {
            throw new IllegalStateException(
                    "System clock is before the configured custom epoch"
            );
        }

        if (timestamp > SnowflakeLayout.MAX_TIMESTAMP) {
            throw new IllegalStateException(
                    "Timestamp exceeds the 41-bit Snowflake limit"
            );
        }

        return timestamp;
    }

    private long waitUntilNextMillis(long previousTimestamp) {
        long timestamp = currentTimestamp();

        while (timestamp <= previousTimestamp) {
            timestamp = currentTimestamp();
        }

        return timestamp;
    }
}