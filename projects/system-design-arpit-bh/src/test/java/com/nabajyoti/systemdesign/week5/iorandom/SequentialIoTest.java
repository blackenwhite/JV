package com.nabajyoti.systemdesign.week5.iorandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SequentialIoTest {

    private static final int BLOCKS = 4;
    private static final long SIZE_BYTES = (long) BLOCKS * IoBenchmarkConfig.BLOCK_SIZE;

    @TempDir
    Path tempDir;

    @Test
    void writeReportsBytesAndOperationsForEveryBlock() throws Exception {
        Path file = tempDir.resolve("seq.bin");

        IoRunResult result = SequentialIo.write(
                file, SIZE_BYTES, IoBenchmarkConfig.BLOCK_SIZE);

        assertEquals(SIZE_BYTES, result.bytes());
        assertEquals(BLOCKS, result.operations());
        assertTrue(result.elapsedNanos() > 0L);
        assertEquals(SIZE_BYTES, java.nio.file.Files.size(file));
    }

    @Test
    void readRoundTripsBlockIndexesWrittenSequentially() throws Exception {
        Path file = tempDir.resolve("seq-roundtrip.bin");

        SequentialIo.write(file, SIZE_BYTES, IoBenchmarkConfig.BLOCK_SIZE);
        IoRunResult readResult = SequentialIo.read(
                file, SIZE_BYTES, IoBenchmarkConfig.BLOCK_SIZE);

        assertEquals(SIZE_BYTES, readResult.bytes());
        assertEquals(BLOCKS, readResult.operations());
        assertTrue(readResult.elapsedNanos() > 0L);

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(IoBenchmarkConfig.BLOCK_SIZE);
            for (int i = 0; i < BLOCKS; i++) {
                buffer.clear();
                int bytesRead = channel.read(buffer);
                assertEquals(IoBenchmarkConfig.BLOCK_SIZE, bytesRead);
                buffer.flip();
                assertEquals(i, buffer.getLong());
            }
        }
    }
}
