package com.nabajyoti.systemdesign.week5.iorandom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IoBenchmarkConfigTest {

    @Test
    void fileSizeIsExactMultipleOfBlockSize() {
        assertEquals(0, IoBenchmarkConfig.FILE_SIZE_BYTES % IoBenchmarkConfig.BLOCK_SIZE);
        assertEquals(
                IoBenchmarkConfig.FILE_SIZE_BYTES / IoBenchmarkConfig.BLOCK_SIZE,
                IoBenchmarkConfig.BLOCK_COUNT);
        assertTrue(IoBenchmarkConfig.BLOCK_COUNT > 0);
        assertEquals(4096, IoBenchmarkConfig.BLOCK_SIZE);
        assertEquals(1L * 1024 * 1024 * 1024, IoBenchmarkConfig.FILE_SIZE_BYTES);
    }
}
