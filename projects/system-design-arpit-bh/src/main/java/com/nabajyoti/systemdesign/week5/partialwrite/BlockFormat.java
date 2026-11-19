package com.nabajyoti.systemdesign.week5.partialwrite;

public final class BlockFormat {

    public static final int BLOCK_SIZE = 4096;
    public static final int SEQUENCE_NUMBER_BYTES = Long.BYTES;
    public static final int PATTERN_BYTES =
            BLOCK_SIZE - SEQUENCE_NUMBER_BYTES;
    public static final byte PATTERN = (byte) 0x5A;

    private BlockFormat() {
        // Utility class; do not create instances.
    }
}