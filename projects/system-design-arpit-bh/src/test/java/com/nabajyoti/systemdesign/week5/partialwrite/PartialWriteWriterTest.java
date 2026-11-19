package com.nabajyoti.systemdesign.week5.partialwrite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartialWriteWriterTest {

    @Test
    void writesBlocksInSequence(
            @TempDir Path tempDirectory
    ) throws Exception {
        Path file = tempDirectory.resolve("data.bin");

        long totalBytes = BlockFormat.BLOCK_SIZE * 3L;

        PartialWriteWriter writer =
                new PartialWriteWriter(file, totalBytes, 0L);

        writer.write();

        assertEquals(totalBytes, Files.size(file));

        byte[] fileContents = Files.readAllBytes(file);

        for (int sequenceNumber = 0;
             sequenceNumber < 3;
             sequenceNumber++) {

            int start =
                    sequenceNumber * BlockFormat.BLOCK_SIZE;

            int end =
                    start + BlockFormat.BLOCK_SIZE;

            byte[] block = Arrays.copyOfRange(
                    fileContents,
                    start,
                    end
            );

            assertTrue(
                    BlockCodec.isValid(block, sequenceNumber)
            );
        }
    }

    @Test
    void rejectsTotalSizeThatIsNotAWholeNumberOfBlocks(
            @TempDir Path tempDirectory
    ) {
        Path file = tempDirectory.resolve("data.bin");

        assertThrows(
                IllegalArgumentException.class,
                () -> new PartialWriteWriter(
                        file,
                        BlockFormat.BLOCK_SIZE + 1L,
                        0L
                )
        );
    }

    @Test
    void defaultSizeIsExactlyTwentyFiveThousandSixHundredBlocks() {
        long blockCount =
                PartialWriteWriter.DEFAULT_TOTAL_BYTES
                        / BlockFormat.BLOCK_SIZE;

        assertEquals(25_600L, blockCount);
    }
}