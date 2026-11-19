package com.nabajyoti.systemdesign.week5.partialwrite;

import java.util.Objects;

public final class SyncPolicy {

    private final SyncMode mode;
    private final long interval;

    private SyncPolicy(SyncMode mode, long interval) {
        this.mode = Objects.requireNonNull(mode);
        this.interval = interval;
    }

    public static SyncPolicy plain() {
        return new SyncPolicy(SyncMode.PLAIN, 0L);
    }

    public static SyncPolicy everyBlock() {
        return new SyncPolicy(SyncMode.EVERY_BLOCK, 0L);
    }

    public static SyncPolicy everyNBlocks(long n) {
        if (n <= 0) {
            throw new IllegalArgumentException(
                    "n must be greater than zero"
            );
        }

        return new SyncPolicy(SyncMode.EVERY_N_BLOCKS, n);
    }

    public boolean shouldForceAfter(long blocksWritten) {
        if (blocksWritten <= 0) {
            throw new IllegalArgumentException(
                    "blocksWritten must be greater than zero"
            );
        }

        return switch (mode) {
            case PLAIN -> false;
            case EVERY_BLOCK -> true;
            case EVERY_N_BLOCKS -> blocksWritten % interval == 0;
        };
    }
}