package com.nabajyoti.systemdesign.week5.bitcask;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class RecordCodec {

    private RecordCodec() {
    }

    public static byte[] encode(byte[] key, byte[] value) {
        ByteBuffer payload = ByteBuffer.allocate(
                RecordFormat.KEY_SIZE_BYTES + RecordFormat.VALUE_SIZE_BYTES + key.length + value.length);
        payload.putInt(key.length);
        payload.putInt(value.length);
        payload.put(key);
        payload.put(value);
        return withCrc(payload.array());
    }
    public static byte[] encodeTombstone(byte[] key) {
        ByteBuffer payload = ByteBuffer.allocate(
                RecordFormat.KEY_SIZE_BYTES + RecordFormat.VALUE_SIZE_BYTES + key.length);
        payload.putInt(key.length);
        payload.putInt(RecordFormat.TOMBSTONE_VALUE_SIZE);
        payload.put(key);
        return withCrc(payload.array());
    }
    private static byte[] withCrc(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(RecordFormat.CRC_BYTES + payload.length);
        buffer.putInt(RecordChecksum.crc32(payload));
        buffer.put(payload);
        return buffer.array();
    }

    public static LogRecord decode(byte[] bytes) throws CorruptRecordException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int storedCrc = buffer.getInt();

        byte[] payload = Arrays.copyOfRange(bytes, RecordFormat.CRC_BYTES, bytes.length);
        if (storedCrc != RecordChecksum.crc32(payload)) {
            throw new CorruptRecordException("crc mismatch");
        }

        int keySize = buffer.getInt();
        int valueSize = buffer.getInt();

        byte[] key = new byte[keySize];
        buffer.get(key);

        if (valueSize == RecordFormat.TOMBSTONE_VALUE_SIZE) {
            return new LogRecord(key, new byte[0], true);
        }

        byte[] value = new byte[valueSize];
        buffer.get(value);
        return new LogRecord(key, value, false);
    }
}