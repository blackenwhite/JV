package com.nabajyoti.systemdesign.week5.iorandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomIoTest {

    private static final int BLOCKS = 8;
    private static final long SIZE_BYTES = (long) BLOCKS * IoBenchmarkConfig.BLOCK_SIZE;
    private static final long SEED = 42L;

    @TempDir
    Path tempDir;

    @Test
    void writeReportsSameBytesAndOpsAsSequentialWould() throws Exception {
        Path file = tempDir.resolve("random.bin");

        IoRunResult result = RandomIo.write(
                file, SIZE_BYTES, IoBenchmarkConfig.BLOCK_SIZE, SEED);

        assertEquals(SIZE_BYTES, result.bytes());
        assertEquals(BLOCKS, result.operations());
        assertTrue(result.elapsedNanos() > 0L);
        assertEquals(SIZE_BYTES, Files.size(file));
    }

    @Test
    void writeCoversEveryBlockOffsetExactlyOnceWithCorrectIndex()
            throws Exception {
        Path file = tempDir.resolve("random-cover.bin");

        RandomIo.write(file, SIZE_BYTES, IoBenchmarkConfig.BLOCK_SIZE, SEED);

        Set<Long> seenIndexes = new HashSet<>();
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(IoBenchmarkConfig.BLOCK_SIZE);
            for (int i = 0; i < BLOCKS; i++) {
                long offset = (long) i * IoBenchmarkConfig.BLOCK_SIZE;
                buffer.clear();
                channel.position(offset);
                int bytesRead = channel.read(buffer);
                assertEquals(IoBenchmarkConfig.BLOCK_SIZE, bytesRead);
                buffer.flip();
                long index = buffer.getLong();
                assertEquals(i, index);
                seenIndexes.add(index);
            }
        }
        assertEquals(BLOCKS, seenIndexes.size());
    }

    @Test
    void readReportsSameBytesAndOps() throws Exception {
        Path file = tempDir.resolve("random-read.bin");
        RandomIo.write(file, SIZE_BYTES, IoBenchmarkConfig.BLOCK_SIZE, SEED);

        IoRunResult result = RandomIo.read(
                file, SIZE_BYTES, IoBenchmarkConfig.BLOCK_SIZE, SEED + 1);

        assertEquals(SIZE_BYTES, result.bytes());
        assertEquals(BLOCKS, result.operations());
        assertTrue(result.elapsedNanos() > 0L);
    }
}
