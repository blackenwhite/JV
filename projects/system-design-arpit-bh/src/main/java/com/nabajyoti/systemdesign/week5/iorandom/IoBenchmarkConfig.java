package com.nabajyoti.systemdesign.week5.iorandom;

public final class IoBenchmarkConfig {

    public static final int BLOCK_SIZE = 4096;
    public static final long FILE_SIZE_BYTES = 1L * 1024 * 1024 * 1024; // 1 GiB
    public static final long BLOCK_COUNT = FILE_SIZE_BYTES / BLOCK_SIZE;

    private IoBenchmarkConfig() {
        // Utility class; do not create instances.
    }
}
