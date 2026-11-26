package com.nabajyoti.systemdesign.week5.bitcask;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class BitcaskStore implements AutoCloseable {
    private static final int FIRST_FILE_ID = 1;
    private static final Pattern DATA_FILE_NAME = Pattern.compile("data-(\\d+)\\.log");

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
        try {
            recover();
        } catch (IOException error) {
            closeQuietly(error);
            throw error;
        } catch (RuntimeException error) {
            closeQuietly(error);
            throw error;
        }
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
        long recordStart = entry.valueOffset() - RecordFormat.HEADER_BYTES - key.length;
        int recordLength = RecordFormat.HEADER_BYTES + key.length + entry.valueSize();
        ByteBuffer record = ByteBuffer.allocate(recordLength);
        readFully(channel, record, recordStart);
        LogRecord decoded = RecordCodec.decode(record.array());
        return Optional.of(decoded.value());
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

    private void recover() throws IOException {
        List<Integer> fileIds = listDataFileIds();
        if (fileIds.isEmpty()) {
            activeFileId = FIRST_FILE_ID;
            activeChannel = openFile(activeFileId);
            writePosition = 0;
            return;
        }

        int newestFileId = fileIds.getLast();
        for (int fileId : fileIds) {
            FileChannel channel = openFile(fileId);
            boolean newest = fileId == newestFileId;
            long lastValidOffset = replayFile(fileId, channel, newest);
            if (newest) {
                channel.truncate(lastValidOffset);
                activeFileId = fileId;
                activeChannel = channel;
                writePosition = lastValidOffset;
            }
        }
    }

    private List<Integer> listDataFileIds() throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                    .map(path -> path.getFileName().toString())
                    .map(DATA_FILE_NAME::matcher)
                    .filter(Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group(1)))
                    .sorted()
                    .toList();
        }
    }

    private long replayFile(int fileId, FileChannel channel, boolean newest) throws IOException {
        long size = channel.size();
        long offset = 0;
        while(offset < size) {
            long remaining  = size- offset;
            if(remaining < RecordFormat.HEADER_BYTES) {
                if(newest) {
                    return offset;
                }
                throw new IOException("incomplete record in closed file " + fileId);
            }

            ByteBuffer header = ByteBuffer.allocate(RecordFormat.HEADER_BYTES);
            readFully(channel, header, offset);
            header.flip();
            header.getInt();
            int keySize = header.getInt();
            int valueSize = header.getInt();

            boolean tombstone = valueSize == RecordFormat.TOMBSTONE_VALUE_SIZE;
            long bodyLength = tombstone
                    ? Integer.toUnsignedLong(keySize)
                    : Integer.toUnsignedLong(keySize) + Integer.toUnsignedLong(valueSize);

            if(remaining < RecordFormat.HEADER_BYTES + bodyLength) {
                if(newest) {
                    return offset;
                }
                throw new IOException("incomplete record in closed file " + fileId);
            }

            int recordLength = RecordFormat.HEADER_BYTES + (int) bodyLength;
            ByteBuffer record = ByteBuffer.allocate(recordLength);
            readFully(channel, record, offset);

            LogRecord decoded;

            try {
                decoded = RecordCodec.decode(record.array());
            } catch (CorruptRecordException error) {
                if(newest && offset + recordLength == size) {
                    return offset;
                }
                throw error;
            }

            if(decoded.tombstone()) {
                keydir.remove(copyKey(decoded.key()));
            } else {
                long valueOffset = offset + RecordFormat.HEADER_BYTES + decoded.key().length;
                keydir.put(copyKey(decoded.key()),
                        new KeydirEntry(fileId, valueOffset, decoded.value().length));
            }
            offset += recordLength;
        }
        return offset;
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

    private void closeQuietly(Exception primary) {
        try {
            close();
        } catch (IOException error) {
            primary.addSuppressed(error);
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer, long position)
            throws IOException {
        long readPosition = position;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, readPosition);
            if (read < 0) {
                throw new IOException("unexpected end of file");
            }
            readPosition += read;
        }
    }

    private static ByteBuffer copyKey(byte[] key) {
        return ByteBuffer.wrap(Arrays.copyOf(key, key.length));
    }

    public void merge() throws IOException{
        List<Integer> closedFileIds = files.keySet().stream()
                .filter(id -> id!=activeFileId)
                .sorted()
                .toList();
        if(closedFileIds.isEmpty()) {
            return;
        }

        Map<ByteBuffer, KeydirEntry> relocated = new HashMap<>();
        int mergeFileId = 0;
        FileChannel mergeChannel = null;
        long mergePosition = 0;

        for(int fileId : closedFileIds) {
            FileChannel channel = files.get(fileId);
            long size = channel.size();
            long offset = 0;
            while (offset < size) {
                ByteBuffer header = ByteBuffer.allocate(RecordFormat.HEADER_BYTES);
                readFully(channel, header, offset);
                header.flip();
                header.getInt();

                int keySize = header.getInt();
                int valueSize = header.getInt();
                boolean tombstone = valueSize == RecordFormat.TOMBSTONE_VALUE_SIZE;
                long bodyLength = tombstone ? Integer.toUnsignedLong(keySize)
                        : Integer.toUnsignedLong(keySize) + Integer.toUnsignedLong(valueSize);

                ByteBuffer keyBuffer = ByteBuffer.allocate(keySize);
                readFully(channel, keyBuffer, offset + RecordFormat.HEADER_BYTES);
                byte[] key = keyBuffer.array();
                long valueOffset = offset + RecordFormat.HEADER_BYTES + key.length;

                KeydirEntry live = keydir.get(copyKey(key));
                boolean copy = !tombstone
                        && live!= null
                        && live.fileId() == fileId
                        && live.valueOffset() == valueOffset
                        && live.valueSize() == valueSize;

                if(copy) {
                    ByteBuffer value = ByteBuffer.allocate(valueSize);
                    readFully(channel, value, valueOffset);
                    byte[] encoded = RecordCodec.encode(key, value.array());

                    if(mergeChannel == null
                    || mergePosition + encoded.length > fileSizeLimitBytes) {
                        mergeFileId = nextFileId();
                        mergeChannel = openFile(mergeFileId);
                        mergePosition = 0;
                    }

                    ByteBuffer buffer = ByteBuffer.wrap(encoded);
                    while (buffer.hasRemaining()) {
                        int written = mergeChannel.write(
                                buffer, mergePosition + buffer.position()
                        );
                        if(written <= 0) {
                            throw new IOException("failed to append record");
                        }
                    }

                    long newValueOffset =
                            mergePosition + RecordFormat.HEADER_BYTES + key.length;
                    relocated.put(copyKey(key), new KeydirEntry(mergeFileId, newValueOffset, valueSize));
                    mergePosition += encoded.length;
                }
                offset += RecordFormat.HEADER_BYTES + bodyLength;
            }
        }

        keydir.putAll(relocated);

        for(int fileId : closedFileIds) {
            FileChannel channel = files.remove(fileId);
            channel.close();
            Files.delete(directory.resolve("data-" + fileId + ".log"));
        }
    }

    private int nextFileId() {
        int max = 0;
        for(int fileId : files.keySet()) {
            if(fileId > max) {
                max = fileId;
            }
        }
        return max + 1;
    }
}
