package com.nabajyoti.systemdesign.week5.iorandom;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public final class SequentialIo {

    private static final byte FILL = (byte) 0xA5;

    private SequentialIo() {
        // Utility class; do not create instances.
    }

    public static IoRunResult write(Path path, long sizeBytes, int blockSize)
            throws IOException {
        validate(path, sizeBytes, blockSize);
        long operations = sizeBytes / blockSize;
        ByteBuffer buffer = ByteBuffer.allocate(blockSize);

        long startedAt = System.nanoTime();
        try (FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            for (long index = 0; index < operations; index++) {
                fillBlock(buffer, index);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            }
        }
        long elapsedNanos = System.nanoTime() - startedAt;
        return new IoRunResult(sizeBytes, operations, elapsedNanos);
    }

    public static IoRunResult read(Path path, long sizeBytes, int blockSize)
            throws IOException {
        validate(path, sizeBytes, blockSize);
        long operations = sizeBytes / blockSize;
        ByteBuffer buffer = ByteBuffer.allocate(blockSize);

        long startedAt = System.nanoTime();
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            for (long index = 0; index < operations; index++) {
                buffer.clear();
                while (buffer.hasRemaining()) {
                    int read = channel.read(buffer);
                    if (read < 0) {
                        throw new IOException(
                                "Unexpected EOF at block index " + index);
                    }
                }
            }
        }
        long elapsedNanos = System.nanoTime() - startedAt;
        return new IoRunResult(sizeBytes, operations, elapsedNanos);
    }

    private static void fillBlock(ByteBuffer buffer, long index) {
        buffer.clear();
        buffer.putLong(index);
        while (buffer.hasRemaining()) {
            buffer.put(FILL);
        }
        buffer.flip();
    }

    private static void validate(Path path, long sizeBytes, int blockSize) {
        Objects.requireNonNull(path, "path");
        if (blockSize <= 0) {
            throw new IllegalArgumentException("blockSize must be positive");
        }
        if (sizeBytes < 0 || sizeBytes % blockSize != 0) {
            throw new IllegalArgumentException(
                    "sizeBytes must be a non-negative multiple of blockSize");
        }
    }
}
