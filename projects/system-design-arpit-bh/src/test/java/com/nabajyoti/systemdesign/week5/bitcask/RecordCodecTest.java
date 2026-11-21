package com.nabajyoti.systemdesign.week5.bitcask;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordCodecTest {

    @Test
    void encodesValueRecordAsKeySizeValueSizeKeyValue() {
        byte[] key = "city".getBytes(StandardCharsets.UTF_8);
        byte[] value = "London".getBytes(StandardCharsets.UTF_8);

        byte[] encoded = RecordCodec.encode(key, value);

        assertEquals(RecordFormat.HEADER_BYTES + key.length + value.length, encoded.length);

        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        assertEquals(key.length, buffer.getInt());
        assertEquals(value.length, buffer.getInt());

        byte[] encodedKey = new byte[key.length];
        buffer.get(encodedKey);
        assertArrayEquals(key, encodedKey);

        int valueOffset = RecordFormat.HEADER_BYTES + key.length;
        assertEquals(valueOffset, buffer.position());

        byte[] encodedValue = new byte[value.length];
        buffer.get(encodedValue);
        assertArrayEquals(value, encodedValue);

        LogRecord decoded = RecordCodec.decode(encoded);
        assertFalse(decoded.tombstone());
        assertArrayEquals(key, decoded.key());
        assertArrayEquals(value, decoded.value());
    }

    @Test
    void encodesTombstoneWithSentinelSizeAndNoValueBytes() {
        byte[] key = "city".getBytes(StandardCharsets.UTF_8);

        byte[] encoded = RecordCodec.encodeTombstone(key);

        assertEquals(RecordFormat.HEADER_BYTES + key.length, encoded.length);

        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        assertEquals(key.length, buffer.getInt());
        assertEquals(RecordFormat.TOMBSTONE_VALUE_SIZE, buffer.getInt());

        byte[] encodedKey = new byte[key.length];
        buffer.get(encodedKey);
        assertArrayEquals(key, encodedKey);
        assertEquals(0, buffer.remaining());

        LogRecord decoded = RecordCodec.decode(encoded);
        assertTrue(decoded.tombstone());
        assertArrayEquals(key, decoded.key());
        assertEquals(0, decoded.value().length);
    }
}