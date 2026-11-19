package com.nabajyoti.systemdesign.week5.iorandom;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public final class BenchmarkFileCreator {

    private BenchmarkFileCreator() {
        // Utility class; do not create instances.
    }

    public static void create(Path path, long sizeBytes, int blockSize)
            throws IOException {
        Objects.requireNonNull(path, "path");
        if (blockSize <= 0) {
            throw new IllegalArgumentException("blockSize must be positive");
        }
        if (sizeBytes < 0 || sizeBytes % blockSize != 0) {
            throw new IllegalArgumentException(
                    "sizeBytes must be a non-negative multiple of blockSize");
        }

        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        ByteBuffer block = ByteBuffer.allocate(blockSize);
        try (FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            long written = 0;
            while (written < sizeBytes) {
                block.clear();
                while (block.hasRemaining()) {
                    channel.write(block);
                }
                written += blockSize;
            }
        }
    }
}
