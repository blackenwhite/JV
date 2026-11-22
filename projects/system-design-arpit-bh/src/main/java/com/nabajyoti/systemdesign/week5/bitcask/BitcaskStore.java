package com.nabajyoti.systemdesign.week5.bitcask;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class BitcaskStore implements AutoCloseable {
    private static final int FIRST_FILE_ID = 1;

    private final Path directory;
    private final long fileSizeLimitBytes;
    private final Map<ByteBuffer, KeydirEntry> keydir = new HashMap<>();
    private final Map<Integer, FileChannel> files = new HashMap<>();
    private int activeFileId;
    private FileChannel activeChannel;
    private long writePosition;

    public static BitcaskStore open(Path directory, long fileSizeLimitBytes)
            throws IOException {
        return new BitcaskStore(directory, fileSizeLimitBytes);
    }

    private BitcaskStore(Path directory, long fileSizeLimitBytes) throws IOException {
        this.directory = Objects.requireNonNull(directory, "directory");
        if (fileSizeLimitBytes <= 0) {
            throw new IllegalArgumentException("fileSizeLimitBytes must be positive");
        }
        this.fileSizeLimitBytes = fileSizeLimitBytes;
        this.activeFileId = FIRST_FILE_ID;
        this.activeChannel = openFile(activeFileId);
        this.writePosition = activeChannel.size();
    }

    public void put(byte[] key, byte[] value) throws IOException {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (value.length == 0) {
            throw new IllegalArgumentException("value must not be empty");
        }

        int encodedLength = RecordFormat.HEADER_BYTES + key.length + value.length;
        if (encodedLength > fileSizeLimitBytes) {
            throw new IllegalArgumentException("record larger than file size limit");
        }

        byte[] encoded = RecordCodec.encode(key, value);
        long recordStart = append(encoded);
        long valueOffset = recordStart + RecordFormat.HEADER_BYTES + key.length;
        keydir.put(copyKey(key), new KeydirEntry(activeFileId, valueOffset, value.length));
    }

    public Optional<byte[]> get(byte[] key) throws IOException {
        Objects.requireNonNull(key, "key");
        KeydirEntry entry = keydir.get(copyKey(key));
        if (entry == null) {
            return Optional.empty();
        }

        FileChannel channel = files.get(entry.fileId());
        ByteBuffer value = ByteBuffer.allocate(entry.valueSize());
        long position = entry.valueOffset();
        while (value.hasRemaining()) {
            int read = channel.read(value, position);
            if (read < 0) {
                throw new IOException("unexpected end of file");
            }
            position += read;
        }
        return Optional.of(value.array());
    }

    public void delete(byte[] key) throws IOException {
        Objects.requireNonNull(key, "key");
        ByteBuffer mapKey = copyKey(key);
        if (!keydir.containsKey(mapKey)) {
            return;
        }

        append(RecordCodec.encodeTombstone(key));
        keydir.remove(mapKey);
    }

    @Override
    public void close() throws IOException {
        IOException firstError = null;
        for (FileChannel channel : files.values()) {
            try {
                channel.close();
            } catch (IOException error) {
                if (firstError == null) {
                    firstError = error;
                } else {
                    firstError.addSuppressed(error);
                }
            }
        }
        files.clear();
        if (firstError != null) {
            throw firstError;
        }
    }

    private long append(byte[] encoded) throws IOException {
        if (writePosition + encoded.length > fileSizeLimitBytes) {
            rollOver();
        }

        long recordStart = writePosition;
        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        while (buffer.hasRemaining()) {
            int written = activeChannel.write(buffer, recordStart + buffer.position());
            if (written <= 0) {
                throw new IOException("failed to append record");
            }
        }
        writePosition = recordStart + encoded.length;
        return recordStart;
    }

    private void rollOver() throws IOException {
        activeFileId++;
        activeChannel = openFile(activeFileId);
        writePosition = 0;
    }

    private FileChannel openFile(int fileId) throws IOException {
        Path path = directory.resolve("data-" + fileId + ".log");
        FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);
        files.put(fileId, channel);
        return channel;
    }

    private static ByteBuffer copyKey(byte[] key) {
        return ByteBuffer.wrap(Arrays.copyOf(key, key.length));
    }
}
