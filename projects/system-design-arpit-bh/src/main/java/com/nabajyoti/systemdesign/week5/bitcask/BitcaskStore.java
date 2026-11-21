package com.nabajyoti.systemdesign.week5.bitcask;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public final class BitcaskStore implements AutoCloseable {
    private static final int FIRST_FILE_ID = 1;

    private final long fileSizeLimitBytes;
    private final Map<ByteBuffer, KeydirEntry> keydir = new HashMap<>();
    private final int activeFileId;
    private final FileChannel activeChannel;
    private long writePosition;

    public static BitcaskStore open(Path directory, long fileSizeLimitBytes)
            throws IOException {
        return new BitcaskStore(directory, fileSizeLimitBytes);
    }

    private BitcaskStore(Path directory, long fileSizeLimitBytes) throws IOException {
        Objects.requireNonNull(directory, "directory");
        if(fileSizeLimitBytes <= 0) {
            throw new IllegalArgumentException("fileSizeLimitBytes must be positive");
        }

        this.fileSizeLimitBytes = fileSizeLimitBytes;
        this.activeFileId = FIRST_FILE_ID;
        Path activeFile = directory.resolve("data-" + activeFileId + ".log");
        this.activeChannel = FileChannel.open(
                activeFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE
        );
        this.writePosition = activeChannel.size();
    }


    public void put(byte[] key, byte[] value) throws IOException {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if(value.length == 0) {
            throw new IllegalArgumentException("value must not be empty");
        }

        byte[] encoded = RecordCodec.encode(key, value);
        long recordStart = writePosition;
        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        while(buffer.hasRemaining()) {
            int written = activeChannel.write(buffer, recordStart+ buffer.position());
            if(written <= 0) {
                throw new IOException("failed to append record");
            }
        }

        writePosition = recordStart + encoded.length;

        long valueOffset = recordStart + RecordFormat.HEADER_BYTES + key.length;
        keydir.put(copyKey(key), new KeydirEntry(activeFileId, valueOffset, value.length));
    }

    public Optional<byte[]> get(byte[] key) throws IOException {
        Objects.requireNonNull(key, "key");
        KeydirEntry entry = keydir.get(copyKey(key));
        if (entry == null) {
            return Optional.empty();
        }

        ByteBuffer value = ByteBuffer.allocate(entry.valueSize());
        long position = entry.valueOffset();
        while (value.hasRemaining()) {
            int read = activeChannel.read(value, position);
            if (read < 0) {
                throw new IOException("unexpected end of file");
            }
            position += read;
        }
        return Optional.of(value.array());
    }



    @Override
    public void close() throws IOException {
        activeChannel.close();
    }

    private static ByteBuffer copyKey(byte[] key) {
        return ByteBuffer.wrap(Arrays.copyOf(key, key.length));
    }
}