package com.nabajyoti.systemdesign.week5.partialwrite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartialWriteInspectorTest {

    @Test
    void inspectsFileContainingOnlyCompleteBlocks(
            @TempDir Path tempDirectory
    ) throws Exception {
        Path file = tempDirectory.resolve("complete.bin");

        Files.write(
                file,
                new byte[BlockFormat.BLOCK_SIZE * 2]
        );

        BlockInspection result =
                PartialWriteInspector.inspect(file);

        assertEquals(
                BlockFormat.BLOCK_SIZE * 2L,
                result.fileSize()
        );
        assertEquals(2L, result.completeBlockCount());
        assertEquals(
                BlockInspection.LastBlockStatus.COMPLETE,
                result.lastBlockStatus()
        );
    }

    @Test
    void detectsCutOffLastBlock(
            @TempDir Path tempDirectory
    ) throws Exception {
        Path file = tempDirectory.resolve("cut-off.bin");

        int fileSize = BlockFormat.BLOCK_SIZE * 2 + 100;

        Files.write(file, new byte[fileSize]);

        BlockInspection result =
                PartialWriteInspector.inspect(file);

        assertEquals(fileSize, result.fileSize());
        assertEquals(2L, result.completeBlockCount());
        assertEquals(
                BlockInspection.LastBlockStatus.CUT_OFF,
                result.lastBlockStatus()
        );
    }

    @Test
    void identifiesEmptyFile(
            @TempDir Path tempDirectory
    ) throws Exception {
        Path file = tempDirectory.resolve("empty.bin");

        Files.createFile(file);

        BlockInspection result =
                PartialWriteInspector.inspect(file);

        assertEquals(0L, result.fileSize());
        assertEquals(0L, result.completeBlockCount());
        assertEquals(
                BlockInspection.LastBlockStatus.NO_BLOCKS,
                result.lastBlockStatus()
        );
    }
}