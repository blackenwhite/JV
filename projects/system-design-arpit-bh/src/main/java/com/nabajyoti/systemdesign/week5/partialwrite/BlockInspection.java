package com.nabajyoti.systemdesign.week5.partialwrite;

public record BlockInspection(
        long fileSize,
        long completeBlockCount,
        LastBlockStatus lastBlockStatus
) {
    public enum LastBlockStatus {
        NO_BLOCKS,
        COMPLETE,
        CUT_OFF
    }
}