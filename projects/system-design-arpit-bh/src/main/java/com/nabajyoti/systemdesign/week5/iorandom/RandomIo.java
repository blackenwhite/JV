package com.nabajyoti.systemdesign.week5.iorandom;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Random;

public final class RandomIo {

    private static final byte FILL = (byte) 0xA5;

    private RandomIo() {
        // Utility class; do not create instances.
    }

    public static IoRunResult write(
            Path path, long sizeBytes, int blockSize, long seed)
            throws IOException {
        validate(path, sizeBytes, blockSize);
        long operations = sizeBytes / blockSize;
        long[] offsets = shuffledOffsets(operations, blockSize, seed);
        ByteBuffer buffer = ByteBuffer.allocate(blockSize);

        long startedAt = System.nanoTime();
        try (FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            for (long offset : offsets) {
                long index = offset / blockSize;
                fillBlock(buffer, index);
                channel.position(offset);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            }
        }
        long elapsedNanos = System.nanoTime() - startedAt;
        return new IoRunResult(sizeBytes, operations, elapsedNanos);
    }

    public static IoRunResult read(
            Path path, long sizeBytes, int blockSize, long seed)
            throws IOException {
        validate(path, sizeBytes, blockSize);
        long operations = sizeBytes / blockSize;
        long[] offsets = shuffledOffsets(operations, blockSize, seed);
        ByteBuffer buffer = ByteBuffer.allocate(blockSize);

        long startedAt = System.nanoTime();
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            for (long offset : offsets) {
                buffer.clear();
                channel.position(offset);
                while (buffer.hasRemaining()) {
                    int read = channel.read(buffer);
                    if (read < 0) {
                        throw new IOException(
                                "Unexpected EOF at offset " + offset);
                    }
                }
            }
        }
        long elapsedNanos = System.nanoTime() - startedAt;
        return new IoRunResult(sizeBytes, operations, elapsedNanos);
    }

    static long[] shuffledOffsets(long operations, int blockSize, long seed) {
        long[] offsets = new long[(int) operations];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = (long) i * blockSize;
        }
        Random random = new Random(seed);
        for (int i = offsets.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            long tmp = offsets[i];
            offsets[i] = offsets[j];
            offsets[j] = tmp;
        }
        return offsets;
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
        long operations = sizeBytes / blockSize;
        if (operations > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "too many blocks to shuffle in one array");
        }
    }
}
