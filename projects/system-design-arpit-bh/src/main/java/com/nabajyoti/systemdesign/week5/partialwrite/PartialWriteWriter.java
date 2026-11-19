package com.nabajyoti.systemdesign.week5.partialwrite;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public final class PartialWriteWriter {

    public static final long DEFAULT_TOTAL_BYTES =
            100L * 1024 * 1024;

    private final Path file;
    private final long totalBytes;
    private final long delayMillis;

    public PartialWriteWriter(
            Path file,
            long totalBytes,
            long delayMillis
    ) {
        this.file = Objects.requireNonNull(file);

        if (totalBytes < 0
                || totalBytes % BlockFormat.BLOCK_SIZE != 0) {
            throw new IllegalArgumentException(
                    "totalBytes must be a non-negative multiple of "
                            + BlockFormat.BLOCK_SIZE
            );
        }

        if (delayMillis < 0) {
            throw new IllegalArgumentException(
                    "delayMillis cannot be negative"
            );
        }

        this.totalBytes = totalBytes;
        this.delayMillis = delayMillis;
    }

    public void write() throws IOException, InterruptedException {
        try (FileChannel channel = FileChannel.open(
                file,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            long blockCount =
                    totalBytes / BlockFormat.BLOCK_SIZE;

            for (long sequenceNumber = 0;
                 sequenceNumber < blockCount;
                 sequenceNumber++) {

                byte[] block =
                        BlockCodec.encode(sequenceNumber);

                ByteBuffer buffer =
                        ByteBuffer.wrap(block);

                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }

                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
            }
        }
    }
}