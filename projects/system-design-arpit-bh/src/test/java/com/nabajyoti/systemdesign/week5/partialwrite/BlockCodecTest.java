package com.nabajyoti.systemdesign.week5.partialwrite;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockCodecTest {

    @Test
    void encodesSequenceNumberAndPattern() {
        byte[] block = BlockCodec.encode(42L);

        assertEquals(BlockFormat.BLOCK_SIZE, block.length);

        long sequenceNumber = ByteBuffer
                .wrap(block)
                .getLong();

        assertEquals(42L, sequenceNumber);

        for (int i = BlockFormat.SEQUENCE_NUMBER_BYTES;
             i < block.length;
             i++) {
            assertEquals(BlockFormat.PATTERN, block[i]);
        }
    }

    @Test
    void validatesCorrectBlock() {
        byte[] block = BlockCodec.encode(42L);

        assertTrue(BlockCodec.isValid(block, 42L));
    }

    @Test
    void rejectsWrongSequenceNumber() {
        byte[] block = BlockCodec.encode(42L);

        assertFalse(BlockCodec.isValid(block, 41L));
    }

    @Test
    void rejectsWrongBlockSize() {
        byte[] incompleteBlock = new byte[100];

        assertFalse(BlockCodec.isValid(incompleteBlock, 42L));
    }
}