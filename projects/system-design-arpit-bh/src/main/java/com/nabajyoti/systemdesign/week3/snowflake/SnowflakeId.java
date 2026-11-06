package com.nabajyoti.systemdesign.week3.snowflake;

public record SnowflakeId(
        long id,
        long timestamp,
        long workerId,
        long sequence
) {
    public SnowflakeId {
        if (id < 0) {
            throw new IllegalArgumentException("id must be non-negative");
        }
    }

    public static SnowflakeId decode(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be non-negative");
        }

        long timestamp = id >>> SnowflakeLayout.TIMESTAMP_SHIFT;
        long workerId = (id >>> SnowflakeLayout.WORKER_ID_SHIFT)
                & SnowflakeLayout.MAX_WORKER_ID;
        long sequence = id & SnowflakeLayout.MAX_SEQUENCE;

        return new SnowflakeId(
                id,
                timestamp,
                workerId,
                sequence
        );
    }

    public long absoluteTimestamp() {
        return timestamp + SnowflakeLayout.CUSTOM_EPOCH;
    }
}
