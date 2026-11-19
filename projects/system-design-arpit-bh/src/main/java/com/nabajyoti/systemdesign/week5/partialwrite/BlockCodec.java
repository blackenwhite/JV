package com.nabajyoti.systemdesign.week5.partialwrite;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class BlockCodec {

    private BlockCodec() {
    }

    public static byte[] encode(long sequenceNumber) {
        byte[] block = new byte[BlockFormat.BLOCK_SIZE];

        ByteBuffer.wrap(block)
                .putLong(sequenceNumber);

        Arrays.fill(
                block,
                BlockFormat.SEQUENCE_NUMBER_BYTES,
                BlockFormat.BLOCK_SIZE,
                BlockFormat.PATTERN
        );

        return block;
    }

    public static boolean isValid(
            byte[] block,
            long expectedSequenceNumber
    ) {
        if (block == null
                || block.length != BlockFormat.BLOCK_SIZE) {
            return false;
        }

        long actualSequenceNumber = ByteBuffer
                .wrap(block)
                .getLong();

        if (actualSequenceNumber != expectedSequenceNumber) {
            return false;
        }

        for (int i = BlockFormat.SEQUENCE_NUMBER_BYTES;
             i < block.length;
             i++) {
            if (block[i] != BlockFormat.PATTERN) {
                return false;
            }
        }

        return true;
    }
}