package com.nabajyoti.systemdesign.week5.partialwrite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockFormatTest {

    @Test
    void definesTheExpectedBlockLayout() {
        assertEquals(4096, BlockFormat.BLOCK_SIZE);
        assertEquals(8, BlockFormat.SEQUENCE_NUMBER_BYTES);
        assertEquals(4088, BlockFormat.PATTERN_BYTES);
    }
}