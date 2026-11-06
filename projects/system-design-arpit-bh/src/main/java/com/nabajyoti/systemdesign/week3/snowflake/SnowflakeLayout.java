package com.nabajyoti.systemdesign.week3.snowflake;

public final class SnowflakeLayout {
    private SnowflakeLayout() {
    }

    public static final long CUSTOM_EPOCH = 1_704_067_200_000L;

    public static final int WORKER_ID_BITS = 10;
    public static final int SEQUENCE_BITS = 12;
    public static final int TIMESTAMP_BITS =
            Long.SIZE - 1 - WORKER_ID_BITS - SEQUENCE_BITS;

    public static final long MAX_WORKER_ID =
            (1L << WORKER_ID_BITS) - 1;

    public static final long MAX_SEQUENCE =
            (1L << SEQUENCE_BITS) - 1;

    public static final long MAX_TIMESTAMP =
            (1L << TIMESTAMP_BITS) - 1;

    public static final int WORKER_ID_SHIFT = SEQUENCE_BITS;
    public static final int TIMESTAMP_SHIFT =
            SEQUENCE_BITS + WORKER_ID_BITS;
}
