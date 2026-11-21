package com.nabajyoti.systemdesign.week5.bitcask;

import java.nio.ByteBuffer;

public final class RecordCodec {

    private RecordCodec() {
    }

    public static byte[] encode(byte[] key, byte[] value) {
        ByteBuffer buffer = ByteBuffer.allocate(
                RecordFormat.HEADER_BYTES + key.length + value.length);
        buffer.putInt(key.length);
        buffer.putInt(value.length);
        buffer.put(key);
        buffer.put(value);
        return buffer.array();
    }

    public static byte[] encodeTombstone(byte[] key) {
        ByteBuffer buffer = ByteBuffer.allocate(
                RecordFormat.HEADER_BYTES + key.length);
        buffer.putInt(key.length);
        buffer.putInt(RecordFormat.TOMBSTONE_VALUE_SIZE);
        buffer.put(key);
        return buffer.array();
    }

    public static LogRecord decode(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
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