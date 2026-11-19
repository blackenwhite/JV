package com.nabajyoti.systemdesign.week5.partialwrite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PartialWriteInspector {

    private PartialWriteInspector() {
    }

    public static BlockInspection inspect(Path file)
            throws IOException {
        long fileSize = Files.size(file);

        long completeBlockCount =
                fileSize / BlockFormat.BLOCK_SIZE;

        long remainingBytes =
                fileSize % BlockFormat.BLOCK_SIZE;

        BlockInspection.LastBlockStatus status;

        if (fileSize == 0) {
            status = BlockInspection.LastBlockStatus.NO_BLOCKS;
        } else if (remainingBytes == 0) {
            status = BlockInspection.LastBlockStatus.COMPLETE;
        } else {
            status = BlockInspection.LastBlockStatus.CUT_OFF;
        }

        return new BlockInspection(
                fileSize,
                completeBlockCount,
                status
        );
    }
}