package com.nabajyoti.systemdesign.week5.iorandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BenchmarkFileCreatorTest {

    @TempDir
    Path tempDir;

    @Test
    void createsFileWithExactRequestedSize() throws Exception {
        Path file = tempDir.resolve("bench.bin");
        long sizeBytes = 3L * IoBenchmarkConfig.BLOCK_SIZE;

        BenchmarkFileCreator.create(file, sizeBytes, IoBenchmarkConfig.BLOCK_SIZE);

        assertEquals(sizeBytes, Files.size(file));
    }

    @Test
    void rejectsSizeThatIsNotAMultipleOfBlockSize() {
        Path file = tempDir.resolve("bad.bin");

        assertThrows(
                IllegalArgumentException.class,
                () -> BenchmarkFileCreator.create(
                        file,
                        IoBenchmarkConfig.BLOCK_SIZE + 1L,
                        IoBenchmarkConfig.BLOCK_SIZE));
    }
}
